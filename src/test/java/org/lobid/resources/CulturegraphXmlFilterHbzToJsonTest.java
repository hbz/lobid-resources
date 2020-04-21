/* Copyright 2020 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package org.lobid.resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.metafacture.biblio.marc21.MarcXmlHandler;
import org.metafacture.elasticsearch.JsonToElasticsearchBulk;
import org.metafacture.flowcontrol.ObjectThreader;
import org.metafacture.io.FileOpener;
import org.metafacture.io.ObjectWriter;
import org.metafacture.json.JsonEncoder;
import org.metafacture.mangling.LiteralToObject;
import org.metafacture.metamorph.Metamorph;
import org.metafacture.strings.StringReader;
import org.metafacture.xml.XmlDecoder;
import org.metafacture.xml.XmlElementSplitter;

/**
 * Test for filtering resources with hbz holdings from culturegraph marcxml,
 * tranforming into JSON and writing this as an elasticsearch bulk json file
 * 
 * @author Pascal Christoph(dr0i)
 **/
@SuppressWarnings("javadoc")
public final class CulturegraphXmlFilterHbzToJsonTest {

	private static final Logger LOG =
			LogManager.getLogger(CulturegraphXmlFilterHbzToJsonTest.class);

	static final String PATH_TO_TEST = "src/test/resources/";
	private static final String JSON_TEST_FILE =
			PATH_TO_TEST + "jsonld-cg/bulk.ndjson";

	private static boolean testFailed = false;

	@BeforeClass
	public static void setup() {
		try {
			Files.deleteIfExists(Paths.get(JSON_TEST_FILE));
		} catch (final IOException e) {
			e.printStackTrace();
		}
		et();
	}

	/*
	 * Extract and transform
	 */
	static void et() {
		final FileOpener opener = new FileOpener();
		opener.setReceiver(new XmlDecoder())
				.setReceiver(
						new XmlElementSplitter("marc:collection", "record")) //
				.setReceiver(new LiteralToObject())
				.setReceiver(new ObjectThreader<String>())//
				.addReceiver(receiverThread()); // one thread for it's working
												// on one file atm
		opener.process(new File(
				PATH_TO_TEST + "/aggregate_auslieferung_20191212.small.marcxml")
						.getAbsolutePath());
		try {
			opener.closeStream();
		} catch (final NullPointerException e) {
			// ignore, see https://github.com/hbz/lobid-resources/issues/1030
		}
	}

	private static StringReader receiverThread() {
		final StringReader sr = new StringReader();
		sr.setReceiver(new XmlDecoder()).setReceiver(new MarcXmlHandler())
				.setReceiver(
						new Metamorph("src/main/resources/morph-cg-to-es.xml"))
				.setReceiver(new JsonEncoder())
				.setReceiver(new JsonToElasticsearchBulk("rvk", "cg"))
				.setReceiver(new ObjectWriter<>(JSON_TEST_FILE));
		return sr;
	}

	@SuppressWarnings("static-method")
	@Test
	public void testCreateJsonBulk() {
		LOG.info("Testing creating json bulk");
	}
}
