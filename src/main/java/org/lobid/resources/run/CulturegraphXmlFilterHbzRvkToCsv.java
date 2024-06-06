/* Copyright 2020 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package org.lobid.resources.run;

import java.io.File;
import java.io.IOException;

import org.metafacture.biblio.marc21.MarcXmlHandler;
import org.metafacture.csv.CsvEncoder;
import org.metafacture.json.JsonDecoder;
import org.metafacture.json.JsonEncoder;
import org.metafacture.io.FileOpener;
import org.metafacture.io.ObjectWriter;
import org.metafacture.xml.XmlDecoder;
import org.metafacture.metafix.Metafix;

/**
 * Filter resources with hbz holdings from culturegraph's MARCXML while tranform it with reject()
 * into a CSV file.
 *
 * @author Pascal Christoph (dr0i)
 * @author Tobias Bülte (TobiasNx)
 **/
public final class CulturegraphXmlFilterHbzRvkToCsv {
	private static String OUTPUT_FILE="cg-concordance.tsv";

	public static void main(String... args) {
		String XML_INPUT_FILE = new File(args[0]).getAbsolutePath();

		if (args.length > 1) OUTPUT_FILE = args[1];

		final FileOpener opener = new FileOpener();
		JsonDecoder jsonDecoder = new JsonDecoder();
		jsonDecoder.setRecordPath("records");
		CsvEncoder csvEncoder = new CsvEncoder();
		csvEncoder.setSeparator("\t");
		csvEncoder.setNoQuotes(true);

		try {
			opener.setReceiver(new XmlDecoder()).setReceiver(new MarcXmlHandler())
					.setReceiver(new Metafix("src/main/resources/rvk/cg-to-rvk-csv.fix"))
					.setReceiver(new JsonEncoder())
					.setReceiver(jsonDecoder)
					.setReceiver(csvEncoder)
					.setReceiver(new ObjectWriter<>(OUTPUT_FILE));
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
