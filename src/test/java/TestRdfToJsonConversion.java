
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
	static boolean areTestFilesEqual = true;
	static boolean shouldBeEqual;

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testEquality()
			throws JsonParseException, JsonMappingException, IOException {
		testFiles("adrianInput.nt", "hbz01.es.json", true);
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testWrongContributorOrder()
			throws JsonParseException, JsonMappingException, IOException {
		testFiles("adrianInput.nt", "hbz01.es.wrongContributorOrder.json", false);
	}

	private void compare(final Map<String, Object> expected,
			final Map<String, Object> actual) {
		for (final String s : expected.keySet()) {
			TestRdfToJsonConversion.logger.debug("try to get " + s);
			compareObjects(expected.get(s), actual.get(s));
		}
	}

	private static void compare(final String expected, final String actual) {
		org.junit.Assert.assertTrue(shouldBeEqual ? expected.equals(actual) : true);
		areTestFilesEqual = (expected.equals(actual) ? areTestFilesEqual : false);
	}

	@SuppressWarnings("unchecked")
	private void compareObjects(final Object expected, final Object actual) {
		if (actual instanceof String) {
			TestRdfToJsonConversion.logger.debug("Expected: " + expected);
			TestRdfToJsonConversion.logger.debug("Actual: " + actual);
			compare((String) expected, (String) actual);
		} else if (actual instanceof Set) {
			compare((ArrayList<?>) expected, (Set<Object>) actual);
		} else if (actual instanceof List) {
			compareOrderSensitive(expected.toString(), actual.toString());
		} else if (actual instanceof Map) {
			compare((Map<String, Object>) expected, (Map<String, Object>) actual);
		}
	}

	private static void compare(final ArrayList<?> expected,
			final Set<Object> actual) {
		ArrayList<?> al = expected;
		TestRdfToJsonConversion.logger.debug("Expected unordered: " + expected);
		((Set<?>) actual).forEach(e -> al.remove(e));
		TestRdfToJsonConversion.logger.debug("Actual unordered: " + actual);
		org.junit.Assert.assertTrue(al.size() == 0);
	}

	private static void compareOrderSensitive(String expected, String actual) {
		TestRdfToJsonConversion.logger.debug("Expected ordered: " + expected);
		TestRdfToJsonConversion.logger.debug("Actual ordered: " + actual);
		compare(expected.toString(), actual.toString());
	}

	private void testFiles(String fnameNtriples, String fnameJson,
			boolean _shouldBeEqual)
					throws JsonParseException, JsonMappingException, IOException {
		areTestFilesEqual = true;
		TestRdfToJsonConversion.logger.info("\n\nNew test, begin converting files");
		try (
				InputStream in = Thread.currentThread().getContextClassLoader()
						.getResourceAsStream(fnameNtriples);
				InputStream out = Thread.currentThread().getContextClassLoader()
						.getResourceAsStream(fnameJson)) {
			TestRdfToJsonConversion.shouldBeEqual = _shouldBeEqual;
			final Map<String, Object> actual = new JsonConverter().convert(in,
					RDFFormat.NTRIPLES, "http://lobid.org/resource/HT018454638");
			TestRdfToJsonConversion.logger.info("Creates: ");
			TestRdfToJsonConversion.logger
					.info(new ObjectMapper().writeValueAsString(actual));
			TestRdfToJsonConversion.logger
					.info("\nBegin comparing files: " + fnameNtriples + " against "
							+ fnameJson + ", expect equality:" + _shouldBeEqual);
			final Map<String, Object> expected =
					new ObjectMapper().readValue(out, Map.class);
			compareObjects(expected, actual);
			TestRdfToJsonConversion.logger.info("\nEnd. Equality:" + areTestFilesEqual
					+ ", expected equality:" + _shouldBeEqual);
			org.junit.Assert.assertTrue(areTestFilesEqual == shouldBeEqual);
		}
	}
}
