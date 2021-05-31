/* Copyright 2015 - 2021 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lobid.resources.ElasticsearchIndexer;
import org.lobid.resources.EtikettJson;
import org.lobid.resources.JsonToElasticsearchBulkMap;
import org.metafacture.biblio.marc21.MarcXmlHandler;
import org.metafacture.flowcontrol.ObjectThreader;
import org.metafacture.io.FileOpener;
import org.metafacture.io.TarReader;
import org.metafacture.json.JsonEncoder;
import org.metafacture.mangling.LiteralToObject;
import org.metafacture.metamorph.Metamorph;
import org.metafacture.monitoring.ObjectBatchLogger;
import org.metafacture.monitoring.StreamBatchLogger;
import org.metafacture.strings.StringReader;
import org.metafacture.xml.XmlDecoder;
import org.metafacture.xml.XmlElementSplitter;

import de.hbz.lobid.helper.Email;

/**
 * Transform hbz Alma Marc XML catalog data into lobid elasticsearch JSON-LD and
 * index that into elasticsearch.
 *
 * Input path of data can be an uncompressed file, tar archive or BGZF.
 *
 * @author Pascal Christoph (dr0i)
 *
 */
@SuppressWarnings("javadoc")
public class AlmaMarcXml2lobidJsonEs {
  private static String indexAliasSuffix;
  private static String node;
  private static String cluster;
  private static String indexName;
  private static boolean updateDonotCreateIndex;
  private static String morphFileName = "src/main/resources/alma/alma.xml";
  private static final String INDEXCONFIG = "index-config.json";
  private static final HashMap<String, String> morphVariables = new HashMap<>();
  private static String email = "localhost";
  private static String kind = "";
  private static boolean switchAutomatically = false;
  private static final Logger LOG =
      LogManager.getLogger(AlmaMarcXml2lobidJsonEs.class);
  public static boolean threadAlreadyStarted = false;
  private static String switchAlias1;
  private static String switchAlias2;
  private static String switchMinDocs;
  private static String switchMinSize;
  private static String switchClusterHost;
  public static final String MSG_SUCCESS = "success :) ";
  public static final String MSG_FAIL = "fail :() ";
  static String keyToGetMainId;

  public static void main(String... args) {
    if (threadAlreadyStarted) {
      return;
    }
    AlmaMarcXml2lobidJsonEs.threadAlreadyStarted = true;
    new Thread("AlmaMarcXml2lobidJsonEs") {
      public void run() {
        LOG.info(String.format("Running thread: %s", getName()));
        String usage =
            "<input path>%s<index name>%s<index alias suffix ('NOALIAS' sets it empty)>%s<node>%s<cluster>%s<'update' (will take latest index), 'exact' (will take ->'index name' even when no timestamp is suffixed) , else create new index with actual timestamp>%s<optional: filename of a list of files which shall be ETLed>%s<optional: filename of morph>%s";
        String inputPath = args[0];
        LOG.info(String.format("inputFile=%s", inputPath));
        indexName = args[1];
        String date =
            new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        indexName =
            indexName.matches(".*-20.*") || args[5].equalsIgnoreCase("exact")
                ? indexName
                : indexName + "-" + date;
        indexAliasSuffix = args[2];
        node = args[3];
        cluster = args[4];
        updateDonotCreateIndex = args[5].equalsIgnoreCase("update");
        if (args.length < 6) {
          System.err.println("Usage: AlmaMarcXml2lobidJsonEs" + String
              .format(usage, " ", " ", " ", " ", " ", " ", " ", " ", " "));
          return;
        }
        LOG.info(String.format("using indexName: %s", indexName));
        LOG.info("using indexConfig: " + INDEXCONFIG);
        morphFileName = args.length > 6 ? args[6] : morphFileName;
        LOG.info(String.format("using morph: %s", morphFileName));
        // hbz catalog transformation
        final FileOpener opener = new FileOpener();
        // used when loading a BGZF file
        opener.setDecompressConcatenated(true);
        morphVariables.put("isil", "DE-632");
        morphVariables.put("member", "DE-605");
        morphVariables.put("catalogid", "DE-605");

        XmlElementSplitter xmlElementSplitter = new XmlElementSplitter();
        xmlElementSplitter.setElementName("record");
        keyToGetMainId = System.getProperty("keyToGetMainId", "almaIdMMS");
        LOG.info(String.format("using keyToGetMainId:%s", keyToGetMainId));
        if (inputPath.toLowerCase().endsWith("tar.bz2")
            || inputPath.toLowerCase().endsWith("tar.gz")) {
          LOG.info("recognised as tar archive");
          opener.setReceiver(new TarReader())//
              .setReceiver(new XmlDecoder())//
              .setReceiver(xmlElementSplitter)//
              .setReceiver(new LiteralToObject())//
              .setReceiver(new ObjectThreader<String>())//
              .addReceiver(receiverThread())//
              .addReceiver(receiverThread())//
              .addReceiver(receiverThread())//
              .addReceiver(receiverThread())//
              .addReceiver(receiverThread())//
              .addReceiver(receiverThread());
        } else {
          LOG.info("recognised as BGZF");
          opener.setReceiver(new XmlDecoder())//
              .setReceiver(xmlElementSplitter)//
              .setReceiver(new LiteralToObject())//
              .setReceiver(new ObjectThreader<String>())//
              .addReceiver(receiverThread())//
              .addReceiver(receiverThread())//
              .addReceiver(receiverThread())//
              .addReceiver(receiverThread())//
              .addReceiver(receiverThread())//
              .addReceiver(receiverThread());
        }
        String message = "";
        boolean success = false;
        try {
          opener.process(inputPath);
          opener.closeStream();
          success = true;
          message = "ETL succeeded, index name: " + indexName;
        } catch (Exception e) {
          e.printStackTrace();
          LOG.error(
              String.format("ETL fails: %s %s", e.getMessage(), e.toString()));
          message = e.toString();
          success = false;
        }
        sendMail(kind, success, message);
        if (switchAutomatically) {
          switchAlias();
        }

        AlmaMarcXml2lobidJsonEs.threadAlreadyStarted = false;
        LOG.info(
            "Setting 'AlmaMarcXml2lobidJsonEs.threadAlreadyStarted = false'");
      }
    }.start();

  }

  private static ElasticsearchIndexer getElasticsearchIndexer() {
    ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
    esIndexer.setClustername(cluster);
    esIndexer.setHostname(node);
    esIndexer.setIndexName(indexName);
    esIndexer.setIndexAliasSuffix(indexAliasSuffix);
    esIndexer.setUpdateNewestIndex(updateDonotCreateIndex);
    esIndexer.setIndexConfig(INDEXCONFIG);
    esIndexer.onSetReceiver();
    return esIndexer;
  }

  private static StringReader receiverThread() {
    StreamBatchLogger batchLogger = new StreamBatchLogger();
    batchLogger.setBatchSize(100000);
    ObjectBatchLogger<HashMap<String, String>> objectBatchLogger =
        new ObjectBatchLogger<>();
    objectBatchLogger.setBatchSize(500000);
    MarcXmlHandler marcXmlHandler = new MarcXmlHandler();
    marcXmlHandler.setNamespace(null);
    EtikettJson etikettJson = new EtikettJson();
    etikettJson.setLabelsDirectoryName("../src/main/resources/labels");
    etikettJson.setFilenameOfContext("conf/context.jsonld");
    etikettJson.setGenerateContext(true);
    JsonEncoder jsonEncoder = new JsonEncoder();
    StringReader sr = new StringReader();

    sr.setReceiver(new XmlDecoder())//
        .setReceiver(marcXmlHandler)//
        .setReceiver(new Metamorph(morphFileName, morphVariables))
        .setReceiver(batchLogger)//
        .setReceiver(jsonEncoder)//
        .setReceiver(etikettJson)//
        .setReceiver(new JsonToElasticsearchBulkMap(keyToGetMainId, "ignored",
            "ignored"))//
        .setReceiver(getElasticsearchIndexer());

    return sr;
  }

  public static void setEmail(final String EMAIL) {
    email = EMAIL;
  }

  public static void setKindOfEtl(final String KIND) {
    kind = KIND;
  }

  public static void setSwitchAliasAfterETL(final Boolean SWITCH) {
    switchAutomatically = SWITCH;
  }

  public static void setSwitchVariables(final String ALIAS1,
      final String ALIAS2, final String CLUSTER_HOST,
      final String BASEDUMP_SWITCH_MINDOCS,
      final String BASEDUMP_SWITCH_MINSIZE) {
    switchAlias1 = ALIAS1;
    switchAlias2 = ALIAS2;
    switchClusterHost = CLUSTER_HOST;
    switchMinDocs = BASEDUMP_SWITCH_MINDOCS;
    switchMinSize = BASEDUMP_SWITCH_MINSIZE;
  }

  public static boolean switchAlias() {
    boolean success = false;
    String msg = "Switch alias " + switchAlias1 + " with " + switchAlias2 + ".";
    try {
      success = SwitchEsAlmaAlias.switchAlias(switchAlias1, switchAlias2,
          switchClusterHost, switchMinDocs, switchMinSize);
    } catch (UnknownHostException e) {
      msg = msg + "Couldn't switch alias." + e.toString();
      LOG.error(msg);
    }
    if (success) {
      msg = AlmaMarcXml2lobidJsonEs.MSG_SUCCESS + msg;
      LOG.info(msg);
    } else {
      msg = AlmaMarcXml2lobidJsonEs.MSG_FAIL + msg;
      LOG.error(msg);
    }
    sendMail("Switching alias", success, msg);
    return success;
  }

  public static void sendMail(final String KIND, final boolean SUCCESS,
      final String MESSAGE) {
    try {
      Email.sendEmail("hduser", email,
          "Webhook '" + KIND + "'' " + (SUCCESS ? "success :)" : "fails :("),
          MESSAGE);
    } catch (Exception e) {
      LOG.error(
          String.format("Couldn't send email to %s: %s", email, e.getMessage()),
          e);
    }
  }

}
