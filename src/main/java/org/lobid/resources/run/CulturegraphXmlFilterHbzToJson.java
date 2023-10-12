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
import org.metafacture.strings.StringReader;
import org.metafacture.xml.XmlDecoder;
import org.metafacture.xml.XmlElementSplitter;
import org.metafacture.metafix.Metafix;

/**
 * Filter resources with hbz holdings from culturegraph marcxml while tranform it with reject()
 * into JSON and write this as an elasticsearch bulk json file.
 *
 * @author Pascal Christoph(dr0i) & Tobias Bülte(TobiasNx)
 **/
@SuppressWarnings("javadoc")
public final class CulturegraphXmlFilterHbzToJson {
	private static final String ELASTICSEARCH_INDEX_NAME = "cg";
	private static String JSON_FILE="bulk.ndjson";
	private static final String XML_SPLITTER_ELEMENT = "record";
	private static final String XML_SPLITTER_TOP_ELEMENT = "marc:collection";

	public static void main(String... args) {
		String XML_INPUT_FILE =new File(args[0]).getAbsolutePath();

		if (args.length >1) JSON_FILE=args[1];

		final FileOpener opener = new FileOpener();
		opener.setReceiver(new XmlDecoder())
				.setReceiver(new XmlElementSplitter(XML_SPLITTER_TOP_ELEMENT,
						XML_SPLITTER_ELEMENT)) //
				.setReceiver(new LiteralToObject())
				.setReceiver(new ObjectThreader<String>())//
				.addReceiver(receiverThread()); // one thread for it's working
												// on one file
		opener.process(
				new File(XML_INPUT_FILE).getAbsolutePath());
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
						new Metafix("src/main/resources/fix-cg-to-es.fix"))
				.setReceiver(new JsonEncoder())
				.setReceiver(new JsonToElasticsearchBulk("rvk",
						ELASTICSEARCH_INDEX_NAME))
				.setReceiver(new ObjectWriter<>(JSON_FILE));
		return sr;
	}
}
