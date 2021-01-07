/* Copyright 2020 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package org.lobid.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.metafacture.biblio.marc21.MarcXmlHandler;
import org.metafacture.io.FileOpener;
import org.metafacture.io.ObjectStdoutWriter;
import org.metafacture.io.ObjectWriter;
import org.metafacture.io.TarReader;
import org.metafacture.json.JsonEncoder;
import org.metafacture.mangling.LiteralToObject;
import org.metafacture.metamorph.Metamorph;
import org.metafacture.monitoring.StreamBatchLogger;
import org.metafacture.strings.StringFilter;
import org.metafacture.strings.StringReader;
import org.metafacture.xml.SimpleXmlEncoder;
import org.metafacture.xml.XmlDecoder;
import org.metafacture.xml.XmlElementSplitter;
import org.metafacture.xml.XmlFilenameWriter;

/**
 * Test transformations of Alma MARC21 XML catalog data into lobid JSON-LD.
 *
 * @author Pascal Christoph (dr0i)
 *
 */
public final class AlmaMarc21XmlToLobidJsonTest {

  private static final String MORPH = "src/main/resources/alma/alma.xml";
  private static final File DIRECTORY = new File("src/test/resources/alma/");
  private static final String BIG_ALMA_XML_FILE =
      DIRECTORY + "/HT012734833_etAl.xml.tar.bz2";
  private static final String XML = "xml";
  final HashMap<String, String> morphVariables = new HashMap<>();
  private static boolean GENERATE_TESTDATA =
      System.getProperty("generateTestData", "false").equals("true");
  private static final PrintStream ORIG_OUT = System.out;
  private static final Logger LOG =
      LogManager.getLogger(AlmaMarc21XmlToLobidJsonTest.class);
  private static final String PATTERN_TO_IDENTIFY_XML_RECORDS =
      "HT005207972|HT012734833|KUR00770801";

  /**
   * Sets necessary morph variables.
   */
  @Before
  public void setup() {
    morphVariables.put("isil", "DE-632");
    morphVariables.put("member", "DE-605");
    morphVariables.put("catalogid", "DE-605");
    GENERATE_TESTDATA = true;
    if (GENERATE_TESTDATA) {
     extractXmlTestRecords(PATTERN_TO_IDENTIFY_XML_RECORDS);
    }
  }

  /**
   * Splits xml and extracts records hit by a pattern. Needs 50 secs for 100.000
   * resources in a 44_MB_XML.tar.gz. It's 100 times faster than Filter(morph).
   * This method helps to update the Marc-Xml test files by identifying the
   * records, determining the name of the file using an xpath to get the value
   * from `035 .a` and writes this into the test directory.
   *
   * The files are not pretty printed but untouched, though.
   *
   * @param pattern the pattern which is searched for to identify xml records
   */
  public static void extractXmlTestRecords(final String pattern) {
    long startTime = System.currentTimeMillis();
    XmlElementSplitter xmlElementSplitter = new XmlElementSplitter();
    xmlElementSplitter.setElementName("record");
    XmlElementSplitter xmlElementSplitter_1 = new XmlElementSplitter();
    xmlElementSplitter_1.setElementName("record");
    final StringFilter stringFilter = new StringFilter(pattern);
    XmlFilenameWriter xmlFilenameWriter = new XmlFilenameWriter();
    xmlFilenameWriter
        .setProperty("/record/datafield[@tag='035']/subfield[@code='a']");
    xmlFilenameWriter.setTarget("src/test/resources/alma/");
    StreamBatchLogger logger = new StreamBatchLogger();
    logger.setBatchSize(10);
    FileOpener opener = new FileOpener();
    SimpleXmlEncoder simpleXmlEncoder = new SimpleXmlEncoder();
    simpleXmlEncoder.setSeparateRoots(true);
    opener.setReceiver(new TarReader()) //
        .setReceiver(new XmlDecoder()) //
        .setReceiver(xmlElementSplitter) //
        .setReceiver(logger) //
        .setReceiver(new LiteralToObject()) //
        .setReceiver(stringFilter)
        .setReceiver(new StringReader()) //
        .setReceiver(new XmlDecoder()) //
        .setReceiver(xmlElementSplitter_1) //
        .setReceiver(xmlFilenameWriter);

    opener.process(BIG_ALMA_XML_FILE);
    opener.closeStream();
    long endTime = System.currentTimeMillis();
    LOG.info("Time needed:" + (endTime - startTime) / 1000);
  }

  /**
   * Cleans a bit up. Sets the System.out to the original PrintStream.
   */
  @SuppressWarnings("static-method")
  @After
  public void cleanup() {
    System.setOut(ORIG_OUT);
  }

  /**
   * Tests files from the test directory, one per one. Ignore the dynamically
   * created "endTime".
   *
   * If the System-Property "generateTestData" is set to true the generated data
   * is written into files and thus will act as the new expected data.
   */
  @Test
  public void transformFiles() {
    Arrays.asList(DIRECTORY.listFiles(f -> f.getAbsolutePath().endsWith(XML)))
        .forEach(file -> {
           MarcXmlHandler marcXmlHandler = new MarcXmlHandler();
           marcXmlHandler.setNamespace(null);
          JsonEncoder jsonEncoder = new JsonEncoder();
          jsonEncoder.setPrettyPrinting(true);
          ObjectMapper mapper = new ObjectMapper();
          final String filenameJson =
              file.getAbsolutePath().replaceAll("\\." + XML, "\\.json");
          try {
            FileOpener opener = new FileOpener();
            opener.setReceiver(new XmlDecoder())
                .setReceiver(marcXmlHandler)
                .setReceiver(new Metamorph(MORPH, morphVariables))
                .setReceiver(jsonEncoder);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            System.setOut(ps);
            if (GENERATE_TESTDATA) {
              jsonEncoder.setReceiver(new ObjectWriter<>(filenameJson));
            } else {
              jsonEncoder.setReceiver(new ObjectStdoutWriter<String>());
            }
            opener.process(file.getAbsolutePath());
            opener.closeStream();
            if (!GENERATE_TESTDATA) {
              JsonNode expectedJsonNode =
                  mapper.readTree(new File(filenameJson));
              Object expectedJsonObject =
                  mapper.readValue(expectedJsonNode.toString(), Object.class);
              String expectedJson = mapper.writerWithDefaultPrettyPrinter()
                  .writeValueAsString(expectedJsonObject);
              String actualJson = null;
              actualJson = baos.toString();
              LOG.debug(actualJson);
              // don't test the dynamically created "endTime", e.g:
              // "endTime":"2020-11-30T10:03:42",
              assertEquals(expectedJson.replaceFirst("endTime.{25}", ""),
                  actualJson.substring(0, actualJson.length() - 1)
                      .replaceFirst("endTime.{25}", ""));
            }
          } catch (Exception e) {
            e.printStackTrace();
            fail();
          }
        });
  }
}
