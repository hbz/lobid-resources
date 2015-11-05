
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
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.hbz.lobid.helper.JsonConverter;

/**
 * @author Jan Schnasse
 *
 */
public class TestRdfToJsonConversion {

	final static Logger logger =
			LoggerFactory.getLogger(TestRdfToJsonConversion.class);

	private void compare(final Map<String, Object> expected,
			final Map<String, Object> actual) {
		for (final String s : expected.keySet()) {
			TestRdfToJsonConversion.logger.debug("try to get " + s);
			compareObjects(expected.get(s), actual.get(s));
		}

	}

	private static void compare(final String expected, final String actual) {
		org.junit.Assert.assertEquals(expected, actual);
	}

	@SuppressWarnings("unchecked")
	private void compareObjects(final Object expected, final Object actual) {
		TestRdfToJsonConversion.logger.info("Compare: " + expected + "," + actual);
		if (actual instanceof String) {
			compare((String) expected, (String) actual);
		} else if (actual instanceof Map) {
			compare((Map<String, Object>) expected, (Map<String, Object>) actual);
		} else if (actual instanceof List) {
			compareOrderSensitive((List<Object>) expected, (List<Object>) actual);
		} else {
			TestRdfToJsonConversion.logger.debug("Expected: " + expected);
			TestRdfToJsonConversion.logger.debug("Actual: " + actual);
		}
	}

	private static void compareOrderSensitive(final List<Object> expected,
			final List<Object> actual) {
		for (int i = 0; i < expected.size(); i++) {
			// compareObjects(expected.get(i), actual.get(i));
			TestRdfToJsonConversion.logger.debug("Expected ordered: " + expected);
			TestRdfToJsonConversion.logger.debug("Actual ordered: " + actual);
		}
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void test()
			throws JsonParseException, JsonMappingException, IOException {
		try (
				InputStream in = Thread.currentThread().getContextClassLoader()
						.getResourceAsStream("adrianInput.nt");
				InputStream out = Thread.currentThread().getContextClassLoader()
						.getResourceAsStream("hbz01.es.json")) {
			final Map<String, Object> actual = new JsonConverter().convert(in,
					RDFFormat.NTRIPLES, "http://lobid.org/resource/HT018454638");

			TestRdfToJsonConversion.logger.info("Creates: ");
			TestRdfToJsonConversion.logger
					.info(new ObjectMapper().writeValueAsString(actual));
			TestRdfToJsonConversion.logger.info("---------------------------------");
			final Map<String, Object> expected =
					new ObjectMapper().readValue(out, Map.class);
			compare(expected, actual);
		}
	}
}
