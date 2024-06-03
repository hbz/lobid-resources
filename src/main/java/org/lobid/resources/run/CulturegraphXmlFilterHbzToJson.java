/* Copyright 2020 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package org.lobid.resources.run;

import java.io.File;
import java.io.IOException;

import org.metafacture.biblio.marc21.MarcXmlHandler;
import org.metafacture.elasticsearch.JsonToElasticsearchBulk;
import org.metafacture.io.FileOpener;
import org.metafacture.io.ObjectWriter;
import org.metafacture.json.JsonEncoder;
import org.metafacture.xml.XmlDecoder;
import org.metafacture.metafix.Metafix;

/**
 * Filter resources with hbz holdings from culturegraph's MARCXML while tranform it with reject()
 * into JSON and write this as an elasticsearch bulk json file.
 *
 * @author Pascal Christoph (dr0i)
 * @author Tobias Bülte (TobiasNx)
 **/
public final class CulturegraphXmlFilterHbzToJson {
	private static final String ELASTICSEARCH_INDEX_NAME = "cg";
	public static final String ELASTICSEARCH_INDEX_TYPE_NAME="rvk";
	private static String JSON_FILE="bulk.ndjson";

	public static void main(String... args) {
		String XML_INPUT_FILE = new File(args[0]).getAbsolutePath();

		if (args.length > 1) JSON_FILE = args[1];

		final FileOpener opener = new FileOpener();
		try {
			opener.setReceiver(new XmlDecoder()).setReceiver(new MarcXmlHandler())
					.setReceiver(new Metafix("src/main/resources/rvk/cg-to-rvk-json.fix"))
					.setReceiver(new JsonEncoder())
					.setReceiver(new JsonToElasticsearchBulk(ELASTICSEARCH_INDEX_TYPE_NAME, ELASTICSEARCH_INDEX_NAME))
					.setReceiver(new ObjectWriter<>(JSON_FILE));
		} catch (IOException e) {
			e.printStackTrace();
		}
		opener.process(
				new File(XML_INPUT_FILE).getAbsolutePath());
		try {
			opener.closeStream();
		} catch (final NullPointerException e) {
			// ignore, see https://github.com/hbz/lobid-resources/issues/1030
		}
	}
}
