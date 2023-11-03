/* Copyright 2015 - 2021 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.hbz.lobid.helper.HttpPoster;
import org.lobid.resources.ElasticsearchIndexer;
import org.lobid.resources.EtikettJson;
import org.lobid.resources.JsonToElasticsearchBulkMap;
import org.metafacture.biblio.marc21.MarcXmlHandler;
import org.metafacture.flowcontrol.ObjectThreader;
import org.metafacture.io.FileOpener;
import org.metafacture.io.TarReader;
import org.metafacture.json.JsonEncoder;
import org.metafacture.mangling.LiteralToObject;
import org.metafacture.monitoring.StreamBatchLogger;
import org.metafacture.strings.StringReader;
import org.metafacture.xml.XmlDecoder;
import org.metafacture.xml.XmlElementSplitter;
import org.metafacture.metafix.Metafix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.hbz.lobid.helper.Email;

/**
 * Transform hbz Alma Marc XML catalog data into lobid elasticsearch JSON-LD using metafix and
 * index that into elasticsearch.
 * <p>
 * Input path of data can be an uncompressed file, tar archive or BGZF.
 * <p>
 * Also writes an email using {@link de.hbz.lobid.helper.Email}.
 *
 * @author Pascal Christoph (dr0i)
 */
public class AlmaMarcXmlFix2lobidJsonEs {
    public static final String MSG_THREAD_ALREADY_STARTED = "Setting 'AlmaMarcXmlFix2lobidJsonEs.threadAlreadyStarted =";
    private static String indexAliasSuffix;
    private static String node;
    private static String cluster;
    private static String indexName;
    private static boolean updateDonotCreateIndex;
    private static String fixFileName = "src/main/resources/alma/alma.fix";

    private static final String INDEXCONFIG = "alma/index-config.json";
    private static final HashMap<String, String> fixVariables = new HashMap<>();
    private static String mailtoInfo = "localhost";
    private static String mailtoError = "localhost";
    private static String triggerWebhookUrl;
    private static String triggerWebhookData;
    private static String kind = "";
    private static boolean switchAutomatically = false;
    private static final Logger LOG = LoggerFactory.getLogger(AlmaMarcXmlFix2lobidJsonEs.class);
    public static boolean threadAlreadyStarted = false;
    private static String switchAlias1;
    private static String switchAlias2;
    private static String switchMinDocs;
    private static String switchMinSize;
    private static String switchClusterHost;
    public static final String MSG_SUCCESS = "success :) ";
    public static final String MSG_FAIL = "fail :() ";
    static String keyToGetMainId;
    private static final HttpPoster httpPoster = new HttpPoster();

    public static void main(String... args) {
        if (threadAlreadyStarted) {
            LOG.warn("Cannot start your task because a task is already running. Try again later!");
            return;
        }
        AlmaMarcXmlFix2lobidJsonEs.threadAlreadyStarted = true;
        LOG.info(MSG_THREAD_ALREADY_STARTED + " true");
        new Thread("AlmaMarcXmlFix2lobidJsonEs") {
            public void run() {
                long startMilliseconds = System.currentTimeMillis();
                LOG.info(String.format("Running thread: %s", getName()));
                String usage =
                    "<input path>%s<index name>%s<index alias suffix ('NOALIAS' sets it empty)>%s<node>%s<cluster>%s<'update' (will take latest index), 'exact' (will take ->'index name' even when no timestamp is suffixed) , else create new index with actual timestamp>%s<optional: filename of a list of files which shall be ETLed>%s<optional: filename of fix>%s";
                String inputPath = args[0];
                LOG.info(String.format("inputFile(s)=%s", inputPath));
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
                if (args.length < 7) {
                    System.err.println("Usage: AlmaMarcXmlFix2lobidJsonEs" + String
                        .format(usage, " ", " ", " ", " ", " ", " ", " ", " ", " "));
                    return;
                }
                LOG.info(String.format("using indexName: %s", indexName));
                LOG.info("using indexConfig: " + INDEXCONFIG);
                fixFileName = args[6];
                LOG.info(String.format("using fix: %s", fixFileName));
                // hbz catalog transformation
                final FileOpener opener = new FileOpener();
                // used when loading a BGZF file
                opener.setDecompressConcatenated(true);
                fixVariables.put("isil", "DE-632");
                fixVariables.put("member", "DE-605");
                fixVariables.put("catalogid", "DE-605");
                fixVariables.put("createEndTime", "1"); // 1 <=> true
                fixVariables.put("institution-code", "DE-605");
                // the './' is mandatory to get play to use the "conf" directory. Base is the root directory of the fix, which is "alma":
                fixVariables.put("deweyLabels", "./maps/deweyLabels.tsv");
                fixVariables.put("dnbSachgruppen", "./maps/dnbSachgruppen.tsv");
                fixVariables.put("maps-institutions.tsv", "./maps/institutions.tsv");
                fixVariables.put("sublibraryIsil", "../../../../../../lookup-tables/data/almaSublibraryCode2Isil/generated/generatedAlmaSublibraryCode2Isil.tsv");
                fixVariables.put("suppressedLocations", "../../../../../../lookup-tables/data/almaSuppressedLocations/generated/generatedAlmaSuppressedLocations.tsv");                
                fixVariables.put("picaCreatorId2Isil.tsv", "./maps/picaCreatorId2Isil.tsv");
                fixVariables.put("nwbibWikidataLabelTypeCoords.tsv", "./maps/nwbibWikidataLabelTypeCoords.tsv");
                fixVariables.put("classification.tsv", "./maps/classification.tsv");
                fixVariables.put("formangabe.tsv", "./maps/formangabe.tsv");
                fixVariables.put("almaMmsId2rpbId", "../../../../../../lookup-tables/data/almaMmsId2rpbId.tsv");
                fixVariables.put("lobidOrganisationsMapping.tsv", "./maps/lobidOrganisationsMapping.tsv");
                fixVariables.put("hbzowner2sigel.tsv", "./maps/hbzowner2sigel.tsv");
                fixVariables.put("rpb2.ttl", "../../../../../../vocabs/rpb/rpb2.ttl");
                fixVariables.put("rpb-spatial.ttl", "../../../../../../vocabs/rpb/rpb-spatial.ttl");
                fixVariables.put("rpb.ttl", "../../../../../../vocabs/rpb/rpb.ttl");
                fixVariables.put("nwbib.ttl", "../../../../../../vocabs/nwbib/nwbib.ttl");
                fixVariables.put("nwbib-spatial.ttl", "../../../../../../vocabs/nwbib/nwbib-spatial.ttl");
                fixVariables.put("hbzId2zdbId.tsv", "./maps/hbzId2zdbId.tsv.gz");
                fixVariables.put("isil2opac_hbzId.tsv", "../../../../../../lookup-tables/data/opacLinks/isil2opac_hbzId.tsv");         
                fixVariables.put("isil2opac_isbn.tsv", "../../../../../../lookup-tables/data/opacLinks/isil2opac_isbn.tsv");     
                fixVariables.put("isil2opac_issn.tsv", "../../../../../../lookup-tables/data/opacLinks/isil2opac_issn.tsv");     
                fixVariables.put("isil2opac_zdbId.tsv", "../../../../../../lookup-tables/data/opacLinks/isil2opac_zdbId.tsv");
                fixVariables.put("isil2opac_almaMmsId.tsv", "../../../../../../lookup-tables/data/opacLinks/isil2opac_almaMmsId.tsv");        


                XmlElementSplitter xmlElementSplitter = new XmlElementSplitter();
                xmlElementSplitter.setElementName("record");
                keyToGetMainId = System.getProperty("keyToGetMainId", "almaMmsId");
                LOG.info(String.format("using keyToGetMainId:%s", keyToGetMainId));
                if (inputPath.toLowerCase().endsWith("tar.bz2")
                    || inputPath.toLowerCase().endsWith("tar.gz")) {
                    LOG.info("recognised as tar archive");
                    opener.setReceiver(new TarReader())//
                        .setReceiver(new XmlDecoder())//
                        .setReceiver(xmlElementSplitter)//
                        .setReceiver(new LiteralToObject())//
                        .setReceiver(new ObjectThreader<>())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread());
                }
                else {
                    LOG.info("recognised as BGZF");
                    opener.setReceiver(new XmlDecoder())//
                        .setReceiver(xmlElementSplitter)//
                        .setReceiver(new LiteralToObject())//
                        .setReceiver(new ObjectThreader<>())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread())//
                        .addReceiver(receiverThread());
                }
                StringBuilder message = new StringBuilder();
                boolean success;
                try {
                    String inputPathes[] = inputPath.split(";");
                    for (int i=0;i < inputPathes.length; i++ ) {
                        LOG.info(String.format("Going to process inputFile=%s", inputPathes[i]));
                        opener.process(inputPathes[i]);
                        opener.closeStream();
                    }
                    success = true;
                    message.append("ETL succeeded, index name: " + indexName);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    LOG.error(
                        "ETL fails: ", e.getMessage(), e);
                    message.append(Stream
                        .of(e.getStackTrace())
                        .map(StackTraceElement::toString)
                        .collect(Collectors.joining("\n")));
                    success = false;
                }
                String timeNeeded="Time needed: " + getTimeNeeded(startMilliseconds);
                LOG.info(timeNeeded);
                message.append("\n"+timeNeeded);
                sendMail(kind, success, message.toString());
                if (switchAutomatically) {
                    success = switchAlias();
                }
                if (success) {
                    notifyWebhook();
                }
                AlmaMarcXmlFix2lobidJsonEs.threadAlreadyStarted = false;
                LOG.info(
                    MSG_THREAD_ALREADY_STARTED + " false");
            }
        }.start();

    }

    private static String getTimeNeeded(long startMilliseconds) {
        long tookSeconds = (System.currentTimeMillis()- startMilliseconds) / 1000;
        Duration duration = Duration.ofSeconds(tookSeconds);
        long HH = tookSeconds / 3600;
        long MM = (tookSeconds % 3600) / 60;
        long SS = tookSeconds % 60;
        return String.format("%02dh:%02dm:%02ds", HH, MM, SS);
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
        batchLogger.setBatchSize(10000);
        MarcXmlHandler marcXmlHandler = new MarcXmlHandler();
        marcXmlHandler.setNamespace(null);
        EtikettJson etikettJson = new EtikettJson();
        etikettJson.setLabelsDirectoryName("labels");
        etikettJson.setFilenameOfContext("context.jsonld");
        etikettJson.setGenerateContext(true);
        JsonEncoder jsonEncoder = new JsonEncoder();
        StringReader sr = new StringReader();
        try {
            Metafix metafix = new Metafix(fixFileName, fixVariables);
            LOG.info("Setting strictness to EXPRESSION => metafix will not break if a field makes trouble but rather logs warning");
            metafix.setStrictness(Metafix.Strictness.EXPRESSION);
            metafix.setStrictnessHandlesProcessExceptions(true);
            sr.setReceiver(new XmlDecoder())//
                .setReceiver(marcXmlHandler)//
                .setReceiver(metafix)
                .setReceiver(batchLogger)//
                .setReceiver(jsonEncoder)//
                .setReceiver(etikettJson)//
                .setReceiver(new JsonToElasticsearchBulkMap(keyToGetMainId, "resource",
                    "ignored"))//
                .setReceiver(getElasticsearchIndexer());
        }
        catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        return sr;
    }

    public static void setMailtoInfo(final String EMAIL) {
        mailtoInfo = EMAIL;
    }

    public static void setMailtoError(final String EMAIL) {
        mailtoError = EMAIL;
    }

    public static void setTriggerWebhookUrl(final String url) {
        triggerWebhookUrl = url;
    }

    public static void setTriggerWebhookData(final String data) {
        triggerWebhookData = data;
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
        }
        catch (UnknownHostException e) {
            msg = msg + "Couldn't switch alias." + e;
            LOG.error(msg);
        }
        if (success) {
            msg = AlmaMarcXmlFix2lobidJsonEs.MSG_SUCCESS + msg;
            LOG.info(msg);
        }
        else {
            msg = AlmaMarcXmlFix2lobidJsonEs.MSG_FAIL + msg;
            LOG.error(msg);
        }
        sendMail("Switching alias", success, msg);
        return success;
    }

    public static void sendMail(final String KIND, final boolean SUCCESS,
                                final String MESSAGE) {
        String mailto = (SUCCESS ? mailtoInfo : mailtoError);
        try {
            Email.sendEmail("sol", mailto,
                "Webhook '" + KIND + "' " + (SUCCESS ? "success :)" : "fails :("),
                MESSAGE);
        }
        catch (Exception e) {
            LOG.error(
                String.format("Couldn't send email to %s: %s", mailto, e.getMessage()),
                e);
        }
    }

    private static void notifyWebhook() {
        boolean errored = false;
        LOG.info("Going to notify webhook ... but first waiting 5 minutes as safety distance");

        try {
            Thread.sleep(300000L);
            LOG.info("... waited 5 minutes as safety distance. Starting notifying webhook ");
            httpPoster.setContentType("application/json");
            httpPoster.setUrl(triggerWebhookUrl);
            httpPoster.processData(triggerWebhookData);
        }
        catch (InterruptedException var2) {
            errored = true;
            LOG.error(String.format("Couldn't lay thread to sleep %s", var2.getMessage()), var2);
        }
        catch (Exception var3) {
            errored = true;
            LOG.error(String.format("Couldn't notify webhook listener at '%s' with data '%s': %s", triggerWebhookUrl, triggerWebhookData, var3.getMessage()));
        }

        if (errored) {
            LOG.error(String.format("Couldn't notify webhook listener at '%s' with data '%s'", triggerWebhookUrl, triggerWebhookData));
        }

    }
}

