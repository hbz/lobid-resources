/*Copyright (c) 2015 "hbz"

This file is part of etikett.

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

    final static Logger logger = LoggerFactory
	    .getLogger(TestRdfToJsonConversion.class);

    @SuppressWarnings("javadoc")
    @Test
    public void test() throws JsonParseException, JsonMappingException,
	    IOException {
	try (InputStream in = Thread.currentThread().getContextClassLoader()
		.getResourceAsStream("adrianInput.nt");
		InputStream out = Thread.currentThread()
			.getContextClassLoader()
			.getResourceAsStream("hbz01.es.json")) {
	    Map<String, Object> actual = new JsonConverter()
		    .convert(in, RDFFormat.NTRIPLES,
			    "http://lobid.org/resource/HT018454638")
		    .getJsonResult();

	    logger.info("Creates: ");
	    logger.info(new ObjectMapper().writeValueAsString(actual));
	    logger.info("---------------------------------");
	    Map<String, Object> expected = new ObjectMapper().readValue(out,
		    Map.class);
	    logger.debug("Not tested...");
	    for (String s : expected.keySet()) {
		Object o = expected.get(s);
		if (o instanceof String)
		    org.junit.Assert.assertEquals((String) o, actual.get(s));
		else {
		    logger.debug("Expected: " + o);
		    logger.debug("Actual: " + actual.get(s));
		}
	    }
	}
    }
}
