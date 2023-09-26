/* Copyright 2020-2021 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package org.lobid.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hbz.lobid.helper.JsonFileWriter;
import org.junit.Before;
import org.junit.Test;
import org.metafacture.biblio.marc21.MarcXmlHandler;
import org.metafacture.io.FileOpener;
import org.metafacture.files.DirReader;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test transformations of Alma MARC21 XML catalog data into lobid JSON-LD using Metafix
 *
 * @author Pascal Christoph (dr0i)
 */
public final class AlmaMarc21XmlToLobidJsonMetafixTest {

    private static final String FIX = "src/main/resources/alma/alma.fix";
    private static final String DIRECTORY_NAME = "src/test/resources/alma-fix/";
    private static final File DIRECTORY = new File(DIRECTORY_NAME);
    final HashMap<String, String> fixVariables = new HashMap<>();
    private static final boolean GENERATE_TESTDATA = System.getProperty("generateTestData", "false").equals("true");
    private static final Logger LOG = LoggerFactory.getLogger(AlmaMarc21XmlToLobidJsonMetafixTest.class);
    // try patterns like e.g."662", NOT".*662" (which just would slow down)
    private static final String PATTERN_TO_IDENTIFY_XML_RECORDS = "";

    /**
     * Sets necessary morph variables.
     */
    @Before
    public void setup() {
        fixVariables.put("isil", "DE-632");
        fixVariables.put("member", "DE-605");
        fixVariables.put("catalogid", "DE-605");
        fixVariables.put("createEndTime", "0"); // 0 <=> false
        fixVariables.put("institution-code", "DE-605");
        fixVariables.put("deweyLabels", "src/test/resources/alma/maps/deweyLabels.tsv");
        fixVariables.put("dnbSachgruppen", "src/main/resources/alma/maps/dnbSachgruppen.tsv");
        fixVariables.put("classification.tsv", "src/main/resources/alma/maps/classification.tsv");
        fixVariables.put("formangabe.tsv", "src/main/resources/alma/maps/formangabe.tsv");
        fixVariables.put("maps-institutions.tsv", "src/main/resources/alma/maps/institutions.tsv");
        fixVariables.put("sublibraryIsil", "src/test/resources/alma/maps/generatedAlmaSublibraryCode2Isil.tsv");
        fixVariables.put("suppressedLocations", "src/test/resources/alma/maps/generatedAlmaSuppressedLocations.tsv");
        fixVariables.put("picaCreatorId2Isil.tsv", "src/main/resources/alma/maps/picaCreatorId2Isil.tsv");
        fixVariables.put("nwbibWikidataLabelTypeCoords.tsv", "src/main/resources/alma/maps/nwbibWikidataLabelTypeCoords.tsv");
        fixVariables.put("almaMmsId2rpbId", "src/test/resources/alma/maps/almaMmsId2rpbId.tsv");
        fixVariables.put("lobidOrganisationsMapping.tsv", "src/main/resources/alma/maps/lobidOrganisationsMapping.tsv");
        fixVariables.put("hbzowner2sigel.tsv", "src/main/resources/alma/maps/hbzowner2sigel.tsv");
        fixVariables.put("rpb2.ttl", "src/test/resources/alma/maps/rpb2.ttl");
        fixVariables.put("rpb-spatial.ttl", "src/test/resources/alma/maps/rpb-spatial.ttl");
        fixVariables.put("rpb.ttl", "src/test/resources/alma/maps/rpb.ttl");
        fixVariables.put("hbzId2zdbId.tsv", "src/main/resources/alma/maps/hbzId2zdbId.tsv.gz");
        fixVariables.put("isil2opac_hbzId.tsv", "src/main/resources/alma/maps/isil2opac_hbzId.tsv");         
        fixVariables.put("isil2opac_isbn.tsv", "src/main/resources/alma/maps/isil2opac_hbzId.tsv");     
        fixVariables.put("isil2opac_issn.tsv", "src/main/resources/alma/maps/isil2opac_hbzId.tsv");     
        fixVariables.put("isil2opac_zdbId.tsv", "src/main/resources/alma/maps/isil2opac_hbzId.tsv");   
    }

    /**
     * ETL of archive into filesystem. Tests files from the test directory, one per one. Ignore the dynamically
     * created "endTime".
     * <p>
     * If the System-Property "generateTestData" is set to true the generated data
     * is written into files and thus will act as the new expected data.
     */
    @Test
    public void transformFile() {
        LOG.info("Starting transforming File");
        FileOpener opener = new FileOpener();
        DirReader dirReader = new DirReader();
        dirReader.setFilenamePattern("(.*)xml");
        opener.setDecompressConcatenated(true);
        MarcXmlHandler marcXmlHandler = new MarcXmlHandler();
        marcXmlHandler.setNamespace(null);
        StreamBatchLogger logger = new StreamBatchLogger();
        logger.setBatchSize(10);
        EtikettJson etikettJson = new EtikettJson();
        etikettJson.setLabelsDirectoryName("labels");
        etikettJson.setFilenameOfContext("web/conf/context.jsonld");
        etikettJson.setGenerateContext(true);
        etikettJson.setPretty(true);
        String keyToGetMainId = System.getProperty("keyToGetMainId", "almaMmsId");
        JsonToElasticsearchBulkMap jsonToElasticsearchBulkMap = new JsonToElasticsearchBulkMap(keyToGetMainId, "resource", "ignored");
        try {
            dirReader.setReceiver(opener).setReceiver(new XmlDecoder())//
                .setReceiver(marcXmlHandler)//
                .setReceiver(logger)//
                .setReceiver(new Metafix(FIX, fixVariables))//
                .setReceiver(new JsonEncoder())//
                .setReceiver(etikettJson).setReceiver(jsonToElasticsearchBulkMap);
            if (GENERATE_TESTDATA) {
                jsonToElasticsearchBulkMap.setReceiver(new JsonFileWriter<HashMap<String, String>>(DIRECTORY.getPath()));
            }
            else {
                jsonToElasticsearchBulkMap.setReceiver(new JsonFileWriter<HashMap<String, String>>(DIRECTORY.getPath(), ".tmp"));
            }
            dirReader.process(DIRECTORY_NAME);
            dirReader.closeStream();
            compareGeneratedJson();
        }
        catch (Exception e) {
            LOG.error("Errored when transforming ");
            e.printStackTrace();
            fail();
        } finally {
            deleteTmpFiles();
        }
    }

    private void compareGeneratedJson() {
        Arrays.asList(Objects.requireNonNull(DIRECTORY.listFiles(f -> f.getAbsolutePath().endsWith("tmp")))).forEach(file -> {
            final String filenameJson = file.getAbsolutePath().replaceAll("\\.tmp", "");
            ObjectMapper mapper = new ObjectMapper();
            try {
                String expectedJson = getJsonStringFromFile(filenameJson, mapper);
                String actualJson = getJsonStringFromFile(file.getAbsolutePath(), mapper);
                LOG.debug(actualJson);
                assertEquals(expectedJson, actualJson);
            }
            catch (Exception e) {
                LOG.error("Errored when transforming " + file.getAbsolutePath());
                e.printStackTrace();
                fail();
            }
        });
    }

    private static void deleteTmpFiles() {
        Arrays.asList(Objects.requireNonNull(DIRECTORY.listFiles(f -> f.getAbsolutePath().endsWith("tmp")))).forEach(File::deleteOnExit);
    }

    private String getJsonStringFromFile(String filenameJson, ObjectMapper mapper) throws IOException {
        JsonNode expectedJsonNode = mapper.readTree(new File(filenameJson));
        Object expectedJsonObject = mapper.readValue(expectedJsonNode.toString(), Object.class);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(expectedJsonObject) + "\n";
    }
}
