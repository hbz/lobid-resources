
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
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.Test;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonMappingException;
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
 * @author Jan Schnasse
 * @author Pascal Christoph (dr0i)
 *
 */
public class TestRdfToJsonConversion {

	private static EtikettMakerInterface etikettMaker =
			new EtikettMaker(Thread.currentThread().getContextClassLoader()
					.getResourceAsStream("labels.json"));
	final static Logger logger =
			LoggerFactory.getLogger(TestRdfToJsonConversion.class);
	final static String LOBID_RESOURCES_URI_PREFIX = "http://lobid.org/resource/";
	@SuppressWarnings("unchecked")
	final static Map<String, Object> fullContext =
			(Map<String, Object>) etikettMaker.getContext().get("@context");
	final static String contextUrl =
			"http://lobid.org/context/lobid-resources.json";

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testEquality_caseDirectory() {
		String path = "src/test/resources/input/nt/";
		try {
			Files.walk(Paths.get(path)).filter(Files::isRegularFile)
					.forEach(
							e -> org.junit.Assert.assertTrue(testFiles(e.toString(),
									e.toString().replaceFirst("input/nt", "output/json")
											.replaceFirst("nt$", "json"),
									LOBID_RESOURCES_URI_PREFIX)));
		} catch (Exception e) {
			e.printStackTrace();
			org.junit.Assert.assertFalse(true);
		}
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testEquality_case1() {
		boolean result = testFiles("src/test/resources/adrianInput.nt",
				"src/test/resources/hbz01.es.json", LOBID_RESOURCES_URI_PREFIX);
		TestRdfToJsonConversion.logger
				.info("\n Adrian Input Test - must succeed! \n");
		org.junit.Assert.assertTrue(result);
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testWrongContributorOrder() {
		boolean result = testFiles("src/test/resources/adrianInput.nt",
				"src/test/resources/hbz01.es.wrongContributorOrder.json",
				LOBID_RESOURCES_URI_PREFIX);
		TestRdfToJsonConversion.logger
				.info("\n WrongContributorOrder Test - must fail! \n");
		org.junit.Assert.assertFalse(result);
	}

	private static boolean testFiles(String fnameNtriples, String fnameJson,
			String uri) {
		Map<String, Object> expected = null;
		Map<String, Object> actual = null;
		TestRdfToJsonConversion.logger.info("New test, begin converting files");
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
			try {
				expected = new ObjectMapper().readValue(out, Map.class);
			} // if file to test against not yet exists
			catch (FileNotFoundException | EOFException | JsonMappingException ef) {
				TestRdfToJsonConversion.logger.info("Json file " + fnameJson
						+ " to test against does not yet exist. Will create it now.");
				try {
					TestRdfToJsonConversion.logger.info(
							"Creating: \n" + new ObjectMapper().writeValueAsString(actual));
					JsonConverter.getObjectMapper().defaultPrettyPrintingWriter()
							.writeValue(new File(fnameJson), actual);
					expected =
							new ObjectMapper().readValue(new File(fnameJson), Map.class);
				} catch (IOException e) {
					org.junit.Assert
							.assertFalse("Problems creating file " + e.getMessage(), true);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new CompareJsonMaps().writeFileAndTestJson(
				new ObjectMapper().convertValue(actual, JsonNode.class),
				new ObjectMapper().convertValue(expected, JsonNode.class));
	}

	private static FileInputStream makeFile(String fnameJson) throws IOException {
		File f = new File(fnameJson);
		f.getParentFile().mkdirs();
		f.createNewFile();
		return new FileInputStream(f);
	}
}
