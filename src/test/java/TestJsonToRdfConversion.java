import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
	private static final String SRC_TEST_RESOURCES = "src/test/resources/";
	final static Logger logger =
			LoggerFactory.getLogger(TestJsonToRdfConversion.class);

	@SuppressWarnings({ "javadoc" })
	@Test
	public void test_all() {
		String path = "src/test/resources/reverseTest/input/json/";
		try {
			Files.walk(Paths.get(path)).filter(Files::isRegularFile).forEach(e -> {
				String jsonFilename = new File(SRC_TEST_RESOURCES).toURI()
						.relativize(e.toUri()).getPath();
				String rdfFilename = new File(SRC_TEST_RESOURCES).toURI()
						.relativize(e.toUri()).getPath();
				rdfFilename = rdfFilename.replaceFirst("input/json", "output/nt")
						.replaceFirst("json$", "nt");
				compare(jsonFilename, rdfFilename);
			});
		} catch (Exception e) {
			e.printStackTrace();
			org.junit.Assert.assertFalse(true);
		}
	}

	// "reverseTest/HT012895751.json"
	public void compare(String jsonFilename, String rdfFilename) {
		try {

			logger.info(jsonFilename + " " + rdfFilename);
			// String jsonString = readFileFromClasspath(fileName);
			Map<String, Object> jsonMap = JsonConverter.getObjectMapper()
					.readValue(Thread.currentThread().getContextClassLoader()
							.getResourceAsStream(jsonFilename), Map.class);
			jsonMap.put("@context", Globals.etikette.getContext().get("@context"));
			String jsonString =
					JsonConverter.getObjectMapper().writeValueAsString(jsonMap);
			logger.debug(jsonString);
			String rdf = RdfUtils.readRdfToString(
					new ByteArrayInputStream(jsonString.getBytes("utf-8")),
					RDFFormat.JSONLD, RDFFormat.NTRIPLES, "");
			logger.debug(rdf);
			writeToFileIfNotExists(rdf, rdfFilename);
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	private void writeToFileIfNotExists(String rdf, String fileName)
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
		logger.info("Try to create: " + SRC_TEST_RESOURCES + fileName);
		File f = new File(SRC_TEST_RESOURCES + fileName);
		f.getParentFile().mkdirs();
		f.createNewFile();
		logger.info("Created: " + f.getAbsolutePath());
		Files.write(f.toPath(), rdf.getBytes(), StandardOpenOption.CREATE);
	}
}
