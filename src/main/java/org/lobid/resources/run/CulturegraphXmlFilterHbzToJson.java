/* Copyright 2020 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package org.lobid.resources.run;

import java.io.File;

import org.metafacture.biblio.marc21.MarcXmlHandler;
import org.metafacture.elasticsearch.JsonToElasticsearchBulk;
import org.metafacture.flowcontrol.ObjectThreader;
import org.metafacture.io.FileOpener;
import org.metafacture.io.ObjectWriter;
import org.metafacture.json.JsonEncoder;
import org.metafacture.mangling.LiteralToObject;
import org.metafacture.metamorph.Filter;
import org.metafacture.metamorph.Metamorph;
import org.metafacture.strings.StringReader;
import org.metafacture.xml.XmlDecoder;
import org.metafacture.xml.XmlElementSplitter;

/**
 * Filter resources with hbz holdings from culturegraph marcxml, tranform it
 * into JSON and write this as an elasticsearch bulk json file.
 * 
 * @author Pascal Christoph(dr0i)
 **/
@SuppressWarnings("javadoc")
public final class CulturegraphXmlFilterHbzToJson {
	private static final String JSON_FILE = "bulk.ndjson";

	public static void main(String... args) {
		final FileOpener opener = new FileOpener();
		opener.setReceiver(new XmlDecoder())
				.setReceiver(
						new XmlElementSplitter("marc:collection", "record")) //
				.setReceiver(new LiteralToObject())
				.setReceiver(new ObjectThreader<String>())//
				.addReceiver(receiverThread()); // one thread for it's working
												// on one file atm
		opener.process(new File(args[0]).getAbsolutePath());
		try {
			opener.closeStream();
		} catch (final NullPointerException e) {
			// ignore, see https://github.com/hbz/lobid-resources/issues/1030
		}
	}

	private static StringReader receiverThread() {
		final StringReader sr = new StringReader();
		sr.setReceiver(new XmlDecoder()).setReceiver(new MarcXmlHandler())
				.setReceiver(new Filter(
						new Metamorph("src/main/resources/morph-cg-to-es.xml")))
				.setReceiver(
						new Metamorph("src/main/resources/morph-cg-to-es.xml"))
				.setReceiver(new JsonEncoder())
				.setReceiver(new JsonToElasticsearchBulk("rvk", "cg"))
				.setReceiver(new ObjectWriter<>(JSON_FILE));
		return sr;
	}
}
