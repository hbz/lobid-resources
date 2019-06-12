/* Copyright 2019, hbz. Licensed under the EPL 2.0 */
package org.lobid.resources;

import org.junit.Test;
import org.metafacture.biblio.pica.PicaPlainDecoder;
import org.metafacture.formeta.FormetaEncoder;
import org.metafacture.io.FileOpener;
import org.metafacture.monitoring.ObjectBatchLogger;

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
					.setReceiver(new PicaPlainDecoder())//
					.setReceiver(new FormetaEncoder())//
					.setReceiver(new ObjectBatchLogger());
			fileOpener.process("src/test/resources/thkoeln-korpus_pica_small.txt");
			fileOpener.closeStream();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
