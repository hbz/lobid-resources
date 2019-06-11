/* Copyright 2019, hbz. Licensed under the EPL 2.0 */
package org.lobid.resources;

import org.junit.Test;
import org.metafacture.biblio.pica.PicaDecoder;
import org.metafacture.formeta.FormetaEncoder;
import org.metafacture.io.FileOpener;
import org.metafacture.io.ObjectWriter;
import org.metafacture.metamorph.Metamorph;

/**
 * Transformation from pica tomarcxml.
 * 
 * @author Pascal Christoph (dr0i)
 *
 */
public class Pica2MarcXmlTest {

	/**
	 * 
	 */
	@Test
	public void testPica() {
		final FileOpener fileOpener = new FileOpener();
		try {
			final StringRecordSplitter srs = new StringRecordSplitter("^\\s*$");
			fileOpener//
					.setReceiver(srs)//
					.setReceiver(new PicaDecoder())
					.setReceiver(new Metamorph("src/test/resources/morph-sigel.xml"))//
					.setReceiver(new FormetaEncoder())
					.setReceiver(new ObjectWriter("stdout"));
			fileOpener.process("src/test/resources/thkoeln-korpus_pica_small.txt");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
