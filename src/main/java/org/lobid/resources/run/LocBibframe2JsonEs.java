/* Copyright 2018 hbz, Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.culturegraph.mf.stream.pipe.ObjectBatchLogger;
import org.culturegraph.mf.stream.source.FileOpener;
import org.lobid.resources.ElasticsearchIndexer;
import org.lobid.resources.JsonLdItemSplitter2ElasticsearchJsonLd;
import org.lobid.resources.RdfGraphToJsonLd;
import org.lobid.resources.StringRecordSplitter;
import org.lobid.resources.Triples2RdfModel;

/**
 * Transform loc bibframe ntriples into JSON-LD and index that into
 * elasticsearch.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public class LocBibframe2JsonEs {
	public final static String LOC_CONTEXT =
			"http://lobid.org/resources/loc-context.jsonld";
	public static final String DIRECTORY_TO_LOC_LABELS = "loc-bibframe-labels";
	public final static String INDEX_CONFIG_BIBFRAME =
			"index-config-bibframe.json";
	public final static String RECORD_SPLITTER_MARKER = ".*bibframe/Work> .$";

	public static void main(String... args) {
		String usage =
				"<input path>%s<index name>%s<index alias suffix>%s<node>%s<cluster>%s<'update' (will take latest index), 'exact' (will take ->'index name' even when no timestamp is suffixed) , else create new index with actual timestamp>";
		String inputPath = args[0];
		String indexName = args[1];
		String date = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		indexName =
				indexName.matches(".*-20.*") || args[5].toLowerCase().equals("exact")
						? indexName : indexName + "-" + date;
		String indexAliasSuffix = args[2];
		String node = args[3];
		String cluster = args[4];
		boolean update = args[5].toLowerCase().equals("update");
		System.out.println("It is specified:\n"
				+ String.format(usage, ": " + inputPath + "\n", ": " + indexName + "\n",
						": " + indexAliasSuffix + "\n", ": " + node + "\n", ": " + cluster,
						": " + "\n" + update, " ", " ", " "));
		if (args.length < 6) {
			System.err.println("Usage: LocBibframe2JsonEs"
					+ String.format(usage, " ", " ", " ", " ", " ", " ", " ", " ", " "));
			return;
		}
		String indexConfig = "index-config-bibframe.json";
		System.out.println("using indexConfig: " + indexConfig);
		RdfGraphToJsonLd rdfGraphToJsonLd = new RdfGraphToJsonLd(LOC_CONTEXT);
		rdfGraphToJsonLd.setContextLocationFilname("web/conf/context-loc.jsonld");
		rdfGraphToJsonLd.setRdfTypeToIdentifyRootId(
				"http://id.loc.gov/ontologies/bibframe/Work");
		final FileOpener opener = new FileOpener();
		if (inputPath.toLowerCase().endsWith("bz2")) {
			opener.setCompression("BZIP2");
		} else if (inputPath.toLowerCase().endsWith("gz"))
			opener.setCompression("GZIP");
		ElasticsearchIndexer esIndexer = setElasticsearchIndexer(indexName,
				indexAliasSuffix, node, cluster, update, indexConfig);
		ObjectBatchLogger<String> objectBatchLogger = new ObjectBatchLogger<>();
		objectBatchLogger.setBatchSize(500000);
		final StringRecordSplitter srs =
				new StringRecordSplitter(LocBibframe2JsonEs.RECORD_SPLITTER_MARKER);
		final Triples2RdfModel triple2model = new Triples2RdfModel();
		triple2model.setInput("N-TRIPLE");
		opener.setReceiver(srs)//
				.setReceiver(objectBatchLogger)//
				.setReceiver(rdfGraphToJsonLd)//
				.setReceiver(new JsonLdItemSplitter2ElasticsearchJsonLd(""))//
				.setReceiver(esIndexer);
		opener.process(new File(inputPath)//
				.getAbsolutePath());
		opener.closeStream();
	}

	private static ElasticsearchIndexer setElasticsearchIndexer(String indexName,
			String indexAliasSuffix, String node, String cluster, boolean update,
			String indexConfig) {
		ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
		esIndexer.setClustername(cluster);
		esIndexer.setHostname(node);
		esIndexer.setIndexName(indexName);
		esIndexer.setIndexAliasSuffix(indexAliasSuffix);
		esIndexer.setUpdateNewestIndex(update);
		esIndexer.setIndexConfig(indexConfig);
		esIndexer.lookupMabxmlDeletion = false;
		esIndexer.lookupWikidata = false;
		esIndexer.onSetReceiver();
		return esIndexer;
	}
}
