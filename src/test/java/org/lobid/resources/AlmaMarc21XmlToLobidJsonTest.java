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
import org.metafacture.json.JsonEncoder;
import org.metafacture.metamorph.Metamorph;
import org.metafacture.xml.XmlDecoder;

/**
 * Test transformations of Alma MARC21 XML catalog data into lobid JSON-LD.
 *
 * @author Pascal Christoph (dr0i)
 *
 */
public final class AlmaMarc21XmlToLobidJsonTest {

  private static final String MORPH = "src/main/resources/alma/alma.xml";
  private static final File DIRECTORY = new File("src/test/resources/alma/");
  private static final String XML = "xml";
  final HashMap<String, String> morphVariables = new HashMap<>();
  private static  boolean GENERATE_TESTDATA =
      System.getProperty("generateTestData", "false").equals("true");
  private static final PrintStream ORIG_OUT = System.out;
  private static final Logger LOG =
      LogManager.getLogger(AlmaMarc21XmlToLobidJsonTest.class);

  /**
   * Sets necessary morph variables.
   */
  @Before
  public void setup() {
    morphVariables.put("isil", "DE-632");
    morphVariables.put("member", "DE-605");
    morphVariables.put("catalogid", "DE-605");
  }

  /**
   * Cleans a bit up. Sets the System.out to the original PrintStream.
   */
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
          JsonEncoder jsonEncoder = new JsonEncoder();
          jsonEncoder.setPrettyPrinting(true);
          ObjectMapper mapper = new ObjectMapper();
          final String filenameJson =
              file.getAbsolutePath().replaceAll("\\." + XML, "\\.json");
          try {
            FileOpener opener = new FileOpener();
            opener.setReceiver(new XmlDecoder())
                .setReceiver(new MarcXmlHandler())
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
              JsonNode expectedJsonNode = mapper.readTree(new File(filenameJson));
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
