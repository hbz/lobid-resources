/* Copyright 2020 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package org.lobid.resources;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Test;
import org.lobid.resources.run.CulturegraphXmlFilterHbzRvkToCsv;

/**
 * Test of filtering resources with hbz holdings from culturegraph MARCXML,
 * tranforming into a CSV file.
 *
 * @author Pascal Christoph(dr0i)
 **/
public final class CulturegraphXmlFilterHbzRvkToCsvTest {

	private static final Logger LOG =
			LoggerFactory.getLogger(CulturegraphXmlFilterHbzRvkToCsvTest.class);

	private static final String PATH_TO_TEST = "src/test/resources/";
	public static final String OUTPUT_FILE =
			PATH_TO_TEST + "cg/rvk.tsv";

	private static final String XML_INPUT_FILE = "cg/aggregate_20240507_example.marcxml";

	@SuppressWarnings("static-method")
	@Test
	public void testExtractLookupTableFromCgAsHbzRvk() {
		CulturegraphXmlFilterHbzRvkToCsv.main(PATH_TO_TEST + XML_INPUT_FILE,
				OUTPUT_FILE);
	}

	/**private static void ingest() throws IOException {
		File jsonFile = new File(OUTPUT_FILE);
	}*/


}
