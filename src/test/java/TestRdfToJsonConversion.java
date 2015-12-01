
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
import java.util.Map;

import org.junit.Test;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.hbz.lobid.helper.JsonConverter;

/**
 * 
 * Reads records got from lobid.org in ntriple serialization. These records are
 * mapped to an @see{JsonConverter} and then compared against json files which
 * reflects the expected outcome. This is done using @see{CompareJsonMaps}.
 * 
 * For testing, diffs are done against these files. The order of values (lists
 * vs sets) are taken into acxount.
 *
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

	private static boolean testFiles(String fnameNtriples, String fnameJson,
			String uri) throws JsonParseException, JsonMappingException, IOException {
		TestRdfToJsonConversion.logger.info("\n\nNew test, begin converting files");
		try (
				InputStream in = Thread.currentThread().getContextClassLoader()
						.getResourceAsStream(fnameNtriples);
				InputStream out = Thread.currentThread().getContextClassLoader()
						.getResourceAsStream(fnameJson)) {
			final Map<String, Object> actual =
					new JsonConverter().convert(in, RDFFormat.NTRIPLES, uri);
			TestRdfToJsonConversion.logger.debug("Creates: ");
			TestRdfToJsonConversion.logger
					.debug(new ObjectMapper().writeValueAsString(actual));
			TestRdfToJsonConversion.logger
					.info("\nBegin comparing files: " + fnameNtriples + " against "
							+ fnameJson + ", expect equality:" + false);
			final Map<String, Object> expected =
					new ObjectMapper().readValue(out, Map.class);
			return CompareJsonMaps.writeFileAndTestJson(
					new ObjectMapper().convertValue(actual, JsonNode.class),
					new ObjectMapper().convertValue(expected, JsonNode.class));

		}
	}
}
