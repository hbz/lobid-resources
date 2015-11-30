
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
import java.util.Set;

import org.junit.Test;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

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
				"http://lobid.org/resource/");
		TestRdfToJsonConversion.logger
				.info("\n Adrian Input Test - must succeed! \n");
		org.junit.Assert.assertTrue(result);
	}

	@SuppressWarnings({ "javadoc" })
	@Test
	public void testEquality_case2()
			throws JsonParseException, JsonMappingException, IOException {
		boolean result = testFiles("input/nt/01877/HT018770176.nt",
				"output/json/01877/HT018770176.json", "http://lobid.org/resource/");
		TestRdfToJsonConversion.logger
				.info("\n Blank Node Input Test - must succeed! \n");
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
				.info("\n WrongContributorOrder Test - must fail! \n");
		org.junit.Assert.assertFalse(result);
	}

	private boolean compare(final Map<String, Object> expected,
			final Map<String, Object> actual) {
		for (final String s : expected.keySet()) {
			TestRdfToJsonConversion.logger.trace("try to get " + s);
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
			TestRdfToJsonConversion.logger.debug("Compare String");
			result = expected.equals(actual);
		} else if (actual instanceof List) {
			TestRdfToJsonConversion.logger.debug("Compare List");
			// order sensitive
			result = expected.toString().equals(actual.toString());
		} else if (actual instanceof Map) {
			TestRdfToJsonConversion.logger.debug("Compare Object");
			result =
					compare((Map<String, Object>) expected, (Map<String, Object>) actual);
		} else if (actual instanceof Set) {
			TestRdfToJsonConversion.logger.debug("Compare Set");
			// order insensitive
			result = compareUnordered((List) expected, (Set) actual);
		}
		if (!result) {
			warn(expected, actual);
		}
		return result;
	}

	/**
	 * 
	 * @param expected the expected value comes from json data. all json arrays
	 *          will be converted to lists by jackson object mapper.
	 * @param actual the actual value comes from n-triples. The JsonConverter uses
	 *          sets to express multiple occurrences of objects. The datatype list
	 *          is reserved for rdf-lists.
	 * @return
	 */
	private static boolean compareUnordered(final List<Object> expected,
			final Set<Object> actual) {
		ObjectMapper mapper = new ObjectMapper();
		ArrayNode newExpected = mapper.convertValue(expected, ArrayNode.class);
		ArrayNode newActual = mapper.convertValue(actual, ArrayNode.class);
		return newExpected.equals(newActual);
	}

	private static void warn(Object expected, Object actual) {
		logger.warn("Comparison will fail - Not Equal: \n'" + expected + "'\n'"
				+ actual + "'\n");
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
