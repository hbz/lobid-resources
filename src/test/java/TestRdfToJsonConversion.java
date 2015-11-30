
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * @author Pascal Christoph (dr0i)
 *
 */
public class TestRdfToJsonConversion {

	final static Logger logger =
			LoggerFactory.getLogger(TestRdfToJsonConversion.class);

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testEquality_case1()
			throws JsonParseException, JsonMappingException, IOException {
		boolean result = testFiles("adrianInput.nt", "hbz01.es.json",
				"http://lobid.org/resource/HT018454638");
		TestRdfToJsonConversion.logger
				.info("\nEnd. Equality:" + false + ", expected equality:" + true);
		org.junit.Assert.assertTrue(result);
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testEquality_case2()
			throws JsonParseException, JsonMappingException, IOException {
		boolean result = testFiles("input/nt/01877/HT018770176.nt",
				"output/json/01877/HT018770176.json",
				"http://lobid.org/resource/HT018770176");
		TestRdfToJsonConversion.logger
				.info("\nEnd. Equality:" + false + ", expected equality:" + true);
		org.junit.Assert.assertTrue(result);
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testWrongContributorOrder()
			throws JsonParseException, JsonMappingException, IOException {
		boolean result =
				testFiles("adrianInput.nt", "hbz01.es.wrongContributorOrder.json",
						"http://lobid.org/resource/HT018454638");
		TestRdfToJsonConversion.logger
				.info("\nEnd. Equality:" + false + ", expected equality:" + false);
		org.junit.Assert.assertFalse(result);
	}

	private boolean compare(final Map<String, Object> expected,
			final Map<String, Object> actual) {
		for (final String s : expected.keySet()) {
			TestRdfToJsonConversion.logger.debug("try to get " + s);
			if (!compareObjects(expected.get(s), actual.get(s))) {
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private boolean compareObjects(final Object expected, final Object actual) {

		boolean result = false;

		if (actual instanceof String) {
			TestRdfToJsonConversion.logger.debug("Expected: " + expected);
			TestRdfToJsonConversion.logger.debug("Actual: " + actual);
			result = expected.equals(actual);
			if (!result) {
				warn(expected, actual);
			}
		} else if (actual instanceof Set) {
			// not order sensitive
			result = compare((ArrayList<?>) expected, (Set<Object>) actual);
		} else if (actual instanceof List) {
			// order sensitive
			result = expected.toString().equals(actual.toString());
			if (!result) {
				warn(expected, actual);
			}
		} else if (actual instanceof Map) {
			result =
					compare((Map<String, Object>) expected, (Map<String, Object>) actual);
		}
		return result;
	}

	private static boolean compare(final ArrayList<?> expected,
			final Set<Object> actual) {
		return expected.size() == actual.size();
	}

	private static void warn(Object expected, Object actual) {
		logger.warn("Order sensitive comparison will fail - Not Equal: \n'"
				+ expected + "'\n'" + actual + "'\n" + expected.hashCode() + "\n"
				+ actual.hashCode());
	}

	private boolean testFiles(String fnameNtriples, String fnameJson, String uri)
			throws JsonParseException, JsonMappingException, IOException {
		TestRdfToJsonConversion.logger.info("\n\nNew test, begin converting files");
		try (
				InputStream in = Thread.currentThread().getContextClassLoader()
						.getResourceAsStream(fnameNtriples);
				InputStream out = Thread.currentThread().getContextClassLoader()
						.getResourceAsStream(fnameJson)) {
			final Map<String, Object> actual =
					new JsonConverter().convert(in, RDFFormat.NTRIPLES, uri);
			TestRdfToJsonConversion.logger.info("Creates: ");
			TestRdfToJsonConversion.logger
					.info(new ObjectMapper().writeValueAsString(actual));
			TestRdfToJsonConversion.logger
					.info("\nBegin comparing files: " + fnameNtriples + " against "
							+ fnameJson + ", expect equality:" + false);
			final Map<String, Object> expected =
					new ObjectMapper().readValue(out, Map.class);
			return compareObjects(expected, actual);
		}
	}
}
