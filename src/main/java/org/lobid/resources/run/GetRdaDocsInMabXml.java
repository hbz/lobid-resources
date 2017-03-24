/* Copyright 2016  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.lobid.resources.run;

import java.io.File;

import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.LiteralToObject;
import org.culturegraph.mf.stream.converter.xml.AlephMabXmlHandler;
import org.culturegraph.mf.stream.converter.xml.XmlDecoder;
import org.culturegraph.mf.stream.sink.ObjectWriter;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.stream.source.TarReader;

/**
 * Extract the ID's of hbz01 Aleph Mab XML catalog data entries which are RDA
 * catalogued. Create a newline-separated list with these ID's in filesystem.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class GetRdaDocsInMabXml {

	private static String morph = "morph-getHbz01Rda";

	public static void main(String... args) {
		if (args.length != 1) {
			System.err.println("Usage: GetRdaDocsInMabXml <input path>");
			return;
		}
		String inputPath = args[0];
		final FileOpener opener = new FileOpener();
		if (inputPath.toLowerCase().endsWith("bz2")) {
			opener.setCompression("BZIP2");
		} else if (inputPath.toLowerCase().endsWith("gz"))
			opener.setCompression("GZIP");
		ObjectWriter<String> stdoutWriter = new ObjectWriter<>(morph + ".txt");
		opener.setReceiver(new TarReader()).setReceiver(new XmlDecoder())
				.setReceiver(new AlephMabXmlHandler())
				.setReceiver(new Metamorph("src/main/resources/" + morph + ".xml"))
				.setReceiver(new LiteralToObject()).setReceiver(stdoutWriter);
		opener.process(new File(inputPath).getAbsolutePath());
		opener.closeStream();
	}
}
