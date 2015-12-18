import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.hbz.lobid.helper.Globals;
import de.hbz.lobid.helper.JsonConverter;
import de.hbz.lobid.helper.RdfUtils;

/**
 * @author Jan Schnasse
 *
 */
public class TestJsonToRdfConversion {
	private static final String BASE = "src/test/resources/";
	private static final String REVERSE_IN = "reverseTest/input/json/";
	private static final String REVERSE_OUT = "reverseTest/output/nt/";
	private static final String TEST_IN = "input/nt/";
	private static final String TEST_OUT = "output/json/";
	private static final String TEST_PATH = "src/test/resources/input/nt/";
	final static Logger logger =
			LoggerFactory.getLogger(TestJsonToRdfConversion.class);

	@SuppressWarnings({ "javadoc" })
	@Test
	public void test_all() {

		try {
			Files.walk(Paths.get(BASE + REVERSE_IN)).filter(Files::isRegularFile)
					.forEach(path -> {
						String jsonFilename = getRelativePath(BASE + REVERSE_IN, path);
						String rdfFilename = getRelativePath(BASE + REVERSE_IN, path)
								.replaceFirst("json$", "nt");

						compare(jsonFilename, rdfFilename);
					});
		} catch (Exception e) {
			e.printStackTrace();
			org.junit.Assert.assertFalse(true);
		}
	}

	private String getRelativePath(String relpath, Path path) {
		return new File(relpath).toURI().relativize(path.toUri()).getPath();
	}

	void compare(String jsonFilename, String rdfFilename) {
		try {
			logger.info(jsonFilename + " " + rdfFilename);
			String jsonString = createJsonString(REVERSE_IN + jsonFilename);
			String rdfString = RdfUtils.readRdfToString(
					new ByteArrayInputStream(jsonString.getBytes("utf-8")),
					RDFFormat.JSONLD, RDFFormat.NTRIPLES, "");
			// writeToFileIfNotExists(rdfString, REVERSE_OUT + rdfFilename);
			org.junit.Assert
					.assertTrue(rdfCompare(rdfString, getExpected(rdfFilename)));
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	private String getExpected(String rdfFilename) {
		logger.debug("Read rdf " + TEST_IN + rdfFilename);

		try (InputStream in = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream(TEST_IN + rdfFilename)) {

			String rdfString = RdfUtils.readRdfToString(in, RDFFormat.NTRIPLES,
					RDFFormat.NTRIPLES, "");

			return rdfString;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private boolean rdfCompare(String actual, String expected) {
		String actualWithoutBlankNodes = removeBlankNodes(actual);
		String expectedWithoutBlankNodes = removeBlankNodes(expected);
		String actualSorted = sorted(actualWithoutBlankNodes);
		String expectedSorted = sorted(expectedWithoutBlankNodes);
		return actualSorted.equals(expectedSorted);
	}

	private String removeBlankNodes(String str) {
		return str.replaceAll("_:[^\\ ]*", "")
				.replaceAll("\\^\\^<http://www.w3.org/2001/XMLSchema#string>", "");
	}

	private String sorted(String actualWithoutBlankNodes) {
		String[] list = createList(actualWithoutBlankNodes);
		ArrayList<String> words = new ArrayList<String>(Arrays.asList(list));
		Collections.sort(words, String.CASE_INSENSITIVE_ORDER);
		logger.debug(words.toString());
		return words.toString();
	}

	private String[] createList(String actualWithoutBlankNodes) {
		try (BufferedReader br =
				new BufferedReader(new StringReader(actualWithoutBlankNodes))) {
			List<String> result = new ArrayList<String>();
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

	private String createJsonString(String jsonFilename) {
		try {
			logger.info("Read json " + jsonFilename);
			// String jsonString = readFileFromClasspath(fileName);
			Map<String, Object> jsonMap = JsonConverter.getObjectMapper()
					.readValue(Thread.currentThread().getContextClassLoader()
							.getResourceAsStream(jsonFilename), Map.class);
			jsonMap.put("@context", Globals.etikette.getContext().get("@context"));
			String jsonString =
					JsonConverter.getObjectMapper().writeValueAsString(jsonMap);
			return jsonString;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void writeToFileIfNotExists(String rdf, String fileName)
			throws URISyntaxException, FileNotFoundException, IOException {
		try {
			File rdfFile = Paths.get(Thread.currentThread().getContextClassLoader()
					.getResource(fileName).toURI()).toFile();
		} catch (NullPointerException e) {
			makeFile(rdf, fileName);
		}
	}

	private static String readFileFromClasspath(final String fileName)
			throws IOException, URISyntaxException {
		return new String(Files.readAllBytes(Paths.get(Thread.currentThread()
				.getContextClassLoader().getResource(fileName).toURI())));
	}

	private static void makeFile(String rdf, String fileName) throws IOException {
		logger.info("Try to create: " + BASE + fileName);
		File f = new File(BASE + fileName);
		f.getParentFile().mkdirs();
		f.createNewFile();
		logger.info("Created: " + f.getAbsolutePath());
		Files.write(f.toPath(), rdf.getBytes(), StandardOpenOption.CREATE);
	}
}
