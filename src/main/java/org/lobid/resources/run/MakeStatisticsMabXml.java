/* Copyright 2017  hbz, Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.io.File;

import org.lobid.resources.Stats;
import org.metafacture.biblio.AlephMabXmlHandler;
import org.metafacture.io.FileOpener;
import org.metafacture.io.TarReader;
import org.metafacture.metamorph.Metamorph;
import org.metafacture.xml.XmlDecoder;

/**
 * Extract the different values of hbz01 Aleph Mab XML catalog data entries.
 * Steered by the morph.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class MakeStatisticsMabXml {

	private static String morph = "morph-makeHbz01Statistics";

	public static void main(String... args) {
		String inputPath = "src/test/resources/hbz01XmlClobs.tar.bz2";
		if (args.length != 1) {
			System.out.println(
					"Usage: MakeStatisticsMabXml <input path>\nUsing the default "
							+ inputPath);
		} else
			inputPath = args[0];
		final FileOpener opener = new FileOpener();
		if (inputPath.toLowerCase().endsWith("bz2")) {
			opener.setCompression("BZIP2");
		} else if (inputPath.toLowerCase().endsWith("gz"))
			opener.setCompression("GZIP");
		opener.setReceiver(new TarReader()).setReceiver(new XmlDecoder())
				.setReceiver(new AlephMabXmlHandler())
				.setReceiver(new Metamorph("src/main/resources/" + morph + ".xml"))
				.setReceiver(new Stats());
		opener.process(new File(inputPath).getAbsolutePath());
		opener.closeStream();
	}
}
