/* Copyright 2018 hbz, Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.io.File;

import org.lobid.resources.ElasticsearchIndexer;
import org.lobid.resources.RecordReaderEnhanced;
import org.metafacture.io.FileOpener;

/**
 * Indexes entityfacts into its own ES-Index.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class Entityfacts2Es {
	public static String jsonLdContext =
			"http://hub.culturegraph.org/entityfacts/context/v1/entityfacts.jsonld";

	public static void main(String... args) {
		String inputPath = args[0];
		final FileOpener opener = new FileOpener();
		if (inputPath.toLowerCase().endsWith("bz2")) {
			opener.setCompression("BZIP2");
		} else if (inputPath.toLowerCase().endsWith("gz"))
			opener.setCompression("GZIP");
		RecordReaderEnhanced rre = new RecordReaderEnhanced();
		String indexName = "entityfacts-" + ElasticsearchIndexer.DATE;
		ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
		esIndexer.setClustername("weywot");
		esIndexer.setHostname("weywot3.hbz-nrw.de");
		esIndexer.setIndexName(indexName);
		esIndexer.setIndexAliasSuffix("NOALIAS");
		esIndexer.setUpdateNewestIndex(false);
		esIndexer.setIndexConfig("index-config-entityfacts.json");
		esIndexer.lookupMabxmlDeletion = false;
		esIndexer.onSetReceiver();
		opener.setReceiver(rre).setReceiver(esIndexer);
		opener.process(new File(inputPath).getAbsolutePath());
		opener.closeStream();
	}
}
