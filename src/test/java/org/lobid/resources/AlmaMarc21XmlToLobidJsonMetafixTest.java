/* Copyright 2020-2021 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package org.lobid.resources;

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
import org.metafacture.monitoring.StreamBatchLogger;
import org.metafacture.strings.StringFilter;
import org.metafacture.strings.StringReader;
import org.metafacture.xml.SimpleXmlEncoder;
import org.metafacture.xml.XmlDecoder;
import org.metafacture.xml.XmlElementSplitter;
import org.metafacture.xml.XmlFilenameWriter;
import org.metafacture.metafix.Metafix;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test transformations of Alma MARC21 XML catalog data into lobid JSON-LD using Metafix
 *
 * @author Pascal Christoph (dr0i)
 */
public final class AlmaMarc21XmlToLobidJsonMetafixTest {

    private static final String FIX = "src/main/resources/alma/alma.fix";
    private static final File DIRECTORY = new File("src/test/resources/alma-fix/");
    private static final String BIG_ALMA_XML_FILE =
            "src/test/resources/alma/almaMarcXmlTestFiles.xml.tar.bz2"; //share input file with morph ETL
    private static final String XML = "xml";
    final HashMap<String, String> morphVariables = new HashMap<>();
    private static boolean GENERATE_TESTDATA =
            System.getProperty("generateTestData", "false").equals("true");
    private static final PrintStream ORIG_OUT = System.out;
    private static final Logger LOG =
            LogManager.getLogger(AlmaMarc21XmlToLobidJsonMetafixTest.class);
    // try patterns like e.g."662", NOT".*662" (which just would slow down)
    private static final String PATTERN_TO_IDENTIFY_XML_RECORDS = "";

    /**
     * Sets necessary morph variables.
     */
    @Before
    public void setup() {
        morphVariables.put("isil", "DE-632");
        morphVariables.put("member", "DE-605");
        morphVariables.put("catalogid", "DE-605");
        morphVariables.put("createEndTime", "0"); // 0 <=> false
        morphVariables.put("institution-code", "DE-605");

        if (GENERATE_TESTDATA) {
            extractXmlTestRecords(PATTERN_TO_IDENTIFY_XML_RECORDS);
        }
    }

    /**
     * Splits xml and extracts records hit by a pattern. Needs 50 secs for 100.000
     * resources in a 44_MB_XML.tar.gz. It's 100 times faster than Filter(morph)
     * if the pattern matches early (eg. "001"). This method helps to update the
     * Marc-Xml test files by identifying the records, determining the name of the
     * file using an xpath to get the value from `035 .a` and writes this into the
     * test directory. *
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
        xmlFilenameWriter.setTarget(DIRECTORY.getPath());
        StreamBatchLogger logger = new StreamBatchLogger();
        logger.setBatchSize(10);
        FileOpener opener = new FileOpener();
        SimpleXmlEncoder simpleXmlEncoder = new SimpleXmlEncoder();
        simpleXmlEncoder.setSeparateRoots(true);

        XmlDecoder xmlDecoder = new XmlDecoder(); //
        xmlDecoder.setReceiver(xmlElementSplitter) //
                .setReceiver(logger) //
                .setReceiver(new LiteralToObject()) //
                .setReceiver(stringFilter)//
                .setReceiver(new StringReader()) //
                .setReceiver(new XmlDecoder()) //
                .setReceiver(xmlElementSplitter_1) //
                .setReceiver(xmlFilenameWriter);

        if (BIG_ALMA_XML_FILE.toLowerCase().endsWith("tar.bz2")
                || BIG_ALMA_XML_FILE.toLowerCase().endsWith("tar.gz")) {
            LOG.info("recognised as tar archive");
            opener.setReceiver(new TarReader()).setReceiver(xmlDecoder);
        } else {
            LOG.info("recognised as BGZF");
            opener.setDecompressConcatenated(true);
            opener.setReceiver(xmlDecoder);
        }
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
     * <p>
     * If the System-Property "generateTestData" is set to true the generated data
     * is written into files and thus will act as the new expected data.
     */
    @Test
    public void transformFiles() {
        Arrays.asList(DIRECTORY.listFiles(f -> f.getAbsolutePath().endsWith(XML)))
                .forEach(file -> {
                    ObjectMapper mapper = new ObjectMapper();
                    FileOpener opener = new FileOpener();
                    MarcXmlHandler marcXmlHandler = new MarcXmlHandler();
                    marcXmlHandler.setNamespace(null);
                    EtikettJson etikettJson = new EtikettJson();
                    etikettJson.setLabelsDirectoryName("labels");
                    etikettJson.setFilenameOfContext("web/conf/context.jsonld");
                    etikettJson.setGenerateContext(true);
                    etikettJson.setPretty(true);

                    final String filenameJson =
                            file.getAbsolutePath().replaceAll("\\." + XML, "\\.json");
                    try {
                        opener.setReceiver(new XmlDecoder())//
                                .setReceiver(marcXmlHandler)//
                                .setReceiver(new Metafix(FIX, morphVariables))//
                                .setReceiver(new JsonEncoder())//
                               .setReceiver(etikettJson);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        PrintStream ps = new PrintStream(baos);
                        System.setOut(ps);
                        if (GENERATE_TESTDATA) {
                            etikettJson.setReceiver(new ObjectWriter<>(filenameJson));
                        } else {
                            etikettJson.setReceiver(new ObjectStdoutWriter<String>());
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
                            assertEquals(expectedJson, actualJson);
                        }
                    } catch (Exception e) {
                        LOG.error("Errored when transforming " + file.getAbsolutePath());
                        e.printStackTrace();
                        fail();
                    }
                });
    }
}
