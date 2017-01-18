
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
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.hbz.lobid.helper.EtikettMaker;
import de.hbz.lobid.helper.EtikettMakerInterface;
import de.hbz.lobid.helper.JsonConverter;
import de.hbz.lobid.helper.RdfUtils;

/**
 * @author Jan Schnasse
 * @author Pascal Christoph (dr0i)
 *
 */
public class TestJsonToRdfConversion {

	final static Logger logger =
			LoggerFactory.getLogger(TestJsonToRdfConversion.class);

	private static EtikettMakerInterface etikettMaker =
			new EtikettMaker(Thread.currentThread().getContextClassLoader()
					.getResourceAsStream("labels.json"));
	/**
	 * If "true" all comparisons will be executed and a failed assertion may be
	 * given only once at the end. If "false" the test is cancelled even after the
	 * first fail.
	 */
	private static boolean debugRun = false;

	/**
	 * The relative location of test resources. Needed to access test resources
	 * via normal Filesystem operations
	 */
	private static final String BASE = "src/test/resources/";
	/**
	 * A location relative to BASE. To be used to access resources via class
	 * loader.
	 */
	private static final String REVERSE_IN = "output/json/";
	/**
	 * A location relative to BASE. To be used to access resources via class
	 * loader.
	 */
	private static final String REVERSE_OUT = "reverseTest/output/nt/";

	boolean stringsAreEqual = true;

	/**
	 * If the environment variable is set to "true" the test data shall be updated
	 * and written into filesystem.
	 */
	@BeforeClass
	public static void setup() {
		if (System.getProperty("generateTestData", "false").equals("true"))
			debugRun = true;
		else
			debugRun = false;
		logger.info(
				debugRun ? "Test data will be updated" : "Test data won't be updated");
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void test_all() {
		try {
			Files.walk(Paths.get(BASE + REVERSE_IN)).filter(Files::isRegularFile)
					.forEach(path -> {
						test(path);
					});
			org.junit.Assert.assertTrue(debugRun || stringsAreEqual);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void test(Path path) {
		try {
			String jsonFilename = path.toString();
			String rdfFilename = BASE + REVERSE_OUT + getRdfFileName(path);
			compare(jsonFilename, rdfFilename);
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	private static String getRdfFileName(Path path) {
		String rdfFilename = getRelativePath(BASE + REVERSE_IN, path);
		rdfFilename = rdfFilename.replaceFirst("json$", "nt");
		return rdfFilename;
	}

	private static String getRelativePath(String relpath, Path path) {
		return new File(relpath).toURI().relativize(path.toUri()).getPath();
	}

	void compare(String jsonFilename, String rdfFilename)
			throws FileNotFoundException, IOException {
		String actualRdfString = null;
		logger.info("Compare: " + jsonFilename + " " + rdfFilename);
		actualRdfString = getActual(jsonFilename);
		if (debugRun && !new File(rdfFilename).exists())
			makeFile(actualRdfString, rdfFilename);
		String expectedRdfString = getExpected(rdfFilename);
		boolean areEq = rdfCompare(actualRdfString, expectedRdfString);
		if (!areEq) {
			if (!debugRun) {
				this.stringsAreEqual = false;
				org.junit.Assert.assertTrue(this.stringsAreEqual);
			} else
				makeFile(actualRdfString, rdfFilename);
		}
	}

	private static String getActual(String jsonFilename)
			throws UnsupportedEncodingException {
		String jsonString = createJsonString(jsonFilename);
		String actualRdfString = RdfUtils.readRdfToString(
				new ByteArrayInputStream(jsonString.getBytes("utf-8")),
				RDFFormat.JSONLD, RDFFormat.NTRIPLES, "");
		return actualRdfString;
	}

	private static String getExpected(String rdfFilename) {
		logger.debug("Read rdf " + rdfFilename);
		String rdfString = "";
		try (InputStream in = new FileInputStream(rdfFilename)) {
			rdfString = RdfUtils.readRdfToString(in, RDFFormat.NTRIPLES,
					RDFFormat.NTRIPLES, "");
		} catch (Exception e) {
			logger.error("", e);
		}
		return rdfString;
	}

	private static boolean rdfCompare(String actual, String expected) {
		String actualWithoutBlankNodes = removeBlankNodes(actual);
		String expectedWithoutBlankNodes = removeBlankNodes(expected);
		String[] actualSorted = sorted(actualWithoutBlankNodes);
		String[] expectedSorted = sorted(expectedWithoutBlankNodes);
		return findErrors(actualSorted, expectedSorted);
	}

	private static boolean findErrors(String[] actualSorted,
			String[] expectedSorted) {
		boolean result = true;
		if (actualSorted.length != expectedSorted.length) {
			logger.error("Expected size of " + expectedSorted.length
					+ " is different to actual size " + actualSorted.length);
			result = false;
		} else {
			logger.debug("Expected size of " + expectedSorted.length
					+ " is equal to actual size " + actualSorted.length);
			for (int i = 0; i < actualSorted.length; i++) {
				if (actualSorted[i].equals(expectedSorted[i])) {
					logger.debug("Actual , Expected");
					logger.debug(actualSorted[i]);
					logger.debug(expectedSorted[i]);
					logger.debug("");
				} else {
					logger.error("Error line " + i);
					logger.error("Actual , Expected");
					logger.error(actualSorted[i]);
					logger.error(expectedSorted[i]);
					logger.error("");
					result = false;
					break;
				}
			}
		}
		return result;
	}

	private static String removeBlankNodes(String str) {
		return str.replaceAll("_:[^\\ ]*", "")
				.replaceAll("\\^\\^<http://www.w3.org/2001/XMLSchema#string>", "");
	}

	private static String[] sorted(String actualWithoutBlankNodes) {
		String[] list = createList(actualWithoutBlankNodes);
		ArrayList<String> words = new ArrayList<>(Arrays.asList(list));
		Collections.sort(words, String.CASE_INSENSITIVE_ORDER);
		logger.debug(words.toString());
		String[] ar = new String[words.size()];
		return words.toArray(ar);
	}

	private static String[] createList(String actualWithoutBlankNodes) {
		try (BufferedReader br =
				new BufferedReader(new StringReader(actualWithoutBlankNodes))) {
			List<String> result = new ArrayList<>();
			String line;
			while ((line = br.readLine()) != null) {
				result.add(line);
			}
			String[] ar = new String[result.size()];
			return result.toArray(ar);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String createJsonString(String jsonFilename) {
		logger.debug("Read json " + jsonFilename);
		try (FileInputStream fis = new FileInputStream(jsonFilename)) {
			Map<String, Object> jsonMap =
					JsonConverter.getObjectMapper().readValue(fis, Map.class);
			jsonMap.put("@context", etikettMaker.getContext().get("@context"));
			String jsonString =
					JsonConverter.getObjectMapper().writeValueAsString(jsonMap);
			return jsonString;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void makeFile(String rdf, String fileName) throws IOException {
		logger.info("Try to create: " + fileName);
		Path path = new File(fileName).toPath();
		if (Files.notExists(path.getParent())) {
			Files.createDirectory(path.getParent());
		}
		Files.write(path, rdf.getBytes());
		logger.info("Created: " + path);
	}
}
