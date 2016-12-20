
/*Copyright (c) 2015 "hbz"

This file is part of lobid-rdf-to-json.

etikett is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.hbz.lobid.helper.CompareJsonMaps;
import de.hbz.lobid.helper.EtikettMaker;
import de.hbz.lobid.helper.EtikettMakerInterface;
import de.hbz.lobid.helper.JsonConverter;

/**
 * 
 * Reads records got from lobid.org in ntriple serialization. These records are
 * mapped to an @see{JsonConverter} and then compared against json files which
 * reflects the expected outcome. This is done using @see{CompareJsonMaps}.
 * 
 * For testing, diffs are done against these files. The order of values (lists
 * vs sets) are taken into account.
 *
 * 
 * @author Jan Schnasse
 * @author Pascal Christoph (dr0i)
 *
 */
public class TestRdfToJsonConversion {
	private static boolean generateTestData = false;
	private EtikettMakerInterface etikettMaker =
			new EtikettMaker(Thread.currentThread().getContextClassLoader()
					.getResourceAsStream("labels.json"));
	private EtikettMakerInterface etikettMakerApi_1 =
			new EtikettMaker(Thread.currentThread().getContextClassLoader()
					.getResourceAsStream("labelsApi_1.json"));
	final static Logger logger =
			LoggerFactory.getLogger(TestRdfToJsonConversion.class);
	final static String LOBID_RESOURCES_URI_PREFIX =
			"http://lobid.org/resources/";
	final static String LOBID_RESOURCES_API_1_0_URI_PREFIX =
			"http://lobid.org/resource/";
	final static String contextUrl =
			"http://lobid.org/context/lobid-resources.json";
	final private static String TEST_FILE_CONTRIBUTOR_ORDER =
			"src/test/resources/input/nt/01845/HT018454638.nt";

	static boolean allTestsSuccessful = true;

	/**
	 * If the environment variable is set to "true" the test data shall be updated
	 * and written into filesystem.
	 */
	@BeforeClass
	public static void setup() {
		if (System.getProperty("generateTestData", "false").equals("true"))
			generateTestData = true;
		else
			generateTestData = false;
		TestRdfToJsonConversion.logger.info(generateTestData
				? "Test data will be updated" : "Test data won't be updated");
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testEquality_caseDirectory() {
		String path = "src/test/resources/input/nt";
		try {
			Files.walk(Paths.get(path)).filter(Files::isRegularFile)
					.forEach(e -> testFiles(e.toString(),
							e.toString().replaceFirst("input/nt", "output/json")
									.replaceFirst("nt$", "json"),
							LOBID_RESOURCES_URI_PREFIX, true, etikettMaker));
			org.junit.Assert.assertTrue(generateTestData || allTestsSuccessful);
		} catch (Exception e) {
			e.printStackTrace();
			org.junit.Assert.assertFalse(!generateTestData || true);
		}
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testEqualityApi10Data() {
		String path = "src/test/resources/input_api10/nt/01722/HT017225272";
		try {
			testFiles(path,
					path.replaceFirst("input_api10/nt", "output_api10/json")
							.replaceFirst("nt$", "json"),
					LOBID_RESOURCES_API_1_0_URI_PREFIX, true, etikettMakerApi_1);
			org.junit.Assert.assertTrue(generateTestData || allTestsSuccessful);
		} catch (Exception e) {
			e.printStackTrace();
			org.junit.Assert.assertFalse(!generateTestData || true);
		}
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testEquality_case1() {
		testFiles(TEST_FILE_CONTRIBUTOR_ORDER, "src/test/resources/hbz01.es.json",
				LOBID_RESOURCES_URI_PREFIX, true, etikettMaker);
		TestRdfToJsonConversion.logger
				.info("\n Adrian Input Test - must succeed! \n");
		org.junit.Assert.assertTrue(generateTestData || allTestsSuccessful);
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testWrongContributorOrder() {
		boolean testSuccess = testFiles(TEST_FILE_CONTRIBUTOR_ORDER,
				"src/test/resources/hbz01.es.wrongContributorOrder.json",
				LOBID_RESOURCES_URI_PREFIX, false, etikettMaker);
		TestRdfToJsonConversion.logger
				.info("\n WrongContributorOrder (Test - must fail!). " + (testSuccess
						? "Douh, test didn't failed :(" : "Success because test failed :)")
						+ "\n");
		org.junit.Assert.assertTrue(generateTestData || allTestsSuccessful);
	}

	private static boolean testFiles(String fnameNtriples, String fnameJson,
			String uri, boolean testExpectedToBeEqual,
			EtikettMakerInterface etikettMaker) {
		Map<String, Object> expected = null;
		Map<String, Object> actual = null;
		TestRdfToJsonConversion.logger
				.info("Convert " + fnameNtriples + " to " + fnameJson);
		boolean testResult = false;
		try (InputStream in = new FileInputStream(new File(fnameNtriples));
				InputStream out = new File(fnameJson).exists()
						? new FileInputStream(new File(fnameJson)) : makeFile(fnameJson)) {
			actual = new JsonConverter(etikettMaker).convertLobidData(in,
					RDFFormat.NTRIPLES, uri, contextUrl);
			TestRdfToJsonConversion.logger.debug("Creates: ");
			TestRdfToJsonConversion.logger
					.debug(new ObjectMapper().writeValueAsString(actual));
			TestRdfToJsonConversion.logger.info(
					"Begin comparing files: " + fnameNtriples + " against " + fnameJson);
			expected = new ObjectMapper().readValue(out, Map.class);
			testResult = new CompareJsonMaps().writeFileAndTestJson(
					new ObjectMapper().convertValue(actual, JsonNode.class),
					new ObjectMapper().convertValue(expected, JsonNode.class));
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (testResult != testExpectedToBeEqual) {
			allTestsSuccessful = false;
			if (generateTestData) {
				TestRdfToJsonConversion.logger
						.info("Going to update the test file " + fnameJson);
				makeTestFiles(fnameJson, actual);
			}
		}
		return testResult;
	}

	private static void makeTestFiles(String fnameJson,
			Map<String, Object> actual) {
		TestRdfToJsonConversion.logger.info("Json file " + fnameJson
				+ " to test against does not yet exist. Will create it now.");
		try {
			TestRdfToJsonConversion.logger
					.info("Creating: \n" + new ObjectMapper().writeValueAsString(actual));
			JsonConverter.getObjectMapper().defaultPrettyPrintingWriter()
					.writeValue(new File(fnameJson), actual);
		} catch (IOException e) {
			org.junit.Assert.assertFalse("Problems creating file " + e.getMessage(),
					true);
		}
	}

	private static FileInputStream makeFile(String fnameJson) throws IOException {
		File f = new File(fnameJson);
		f.getParentFile().mkdirs();
		f.createNewFile();
		return new FileInputStream(f);
	}
}
