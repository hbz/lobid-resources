/* Copyright 2018  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.lobid.resources.run;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.stream.pipe.ObjectBatchLogger;
import org.culturegraph.mf.stream.pipe.StreamBatchLogger;
import org.culturegraph.mf.stream.source.FileOpener;
import org.lobid.resources.ElasticsearchIndexer;
import org.lobid.resources.RdfModel2ElasticsearchEtikettJsonLd;
import org.lobid.resources.StringRecordSplitter;
import org.lobid.resources.Triples2RdfModel;

import com.hp.hpl.jena.rdf.model.Model;

/**
 * Transform loc bibframe instances into JSON-LD and index that into
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
	public final static Pattern ROOT_SUBJECT_PATTERN =
			Pattern.compile(".*resources/works/\\p{Alpha}.*");
	public final static String ROOT_ID_PREDICATE =
			"http://id.loc.gov/ontologies/bibframe/adminMetadata";
	public final static String INDEX_CONFIG_BIBFRAME =
			"index-config-bibframe.json";
	public final static String RECORD_SPLITTER_MARKER = ".*bibframe/Work> .$";

	private final static RdfModel2ElasticsearchEtikettJsonLd model2json =
			new RdfModel2ElasticsearchEtikettJsonLd(new File(DIRECTORY_TO_LOC_LABELS),
					LOC_CONTEXT);

	public static void main(String... args) {
		String usage =
				"<input path>%s<index name>%s<index alias suffix>%s<node>%s<cluster>%s<'update' (will take latest index), 'exact' (will take ->'index name' even when no timestamp is suffixed) , else create new index with actual timestamp>%s<optional: filename of index-config>%s<optional: filename of morph>%s<optional: jsonld-context-uri>%s";
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
		String indexConfig = INDEX_CONFIG_BIBFRAME;
		System.out.println("using indexConfig: " + indexConfig);
		System.out.println("using jsonLdContext: " + model2json.getJsonLdContext());
		System.out.println(
				"using jsonLdDirectory: " + model2json.getLabelsDirectoryName());
		model2json.setRootIdPredicate(ROOT_ID_PREDICATE);
		model2json.setIdPatternMainNode(ROOT_SUBJECT_PATTERN);
		System.out
				.println("using rootIdPredicate: " + model2json.getRootIdPredicate());
		DefaultObjectPipe<Model, ObjectReceiver<HashMap<String, String>>> jsonConverter =
				model2json;
		final FileOpener opener = new FileOpener();
		if (inputPath.toLowerCase().endsWith("bz2")) {
			opener.setCompression("BZIP2");
		} else if (inputPath.toLowerCase().endsWith("gz"))
			opener.setCompression("GZIP");
		final Triples2RdfModel triple2model = new Triples2RdfModel();
		triple2model.setInput("N-TRIPLE");
		ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
		esIndexer.setClustername(cluster);
		esIndexer.setHostname(node);
		esIndexer.setIndexName(indexName);
		esIndexer.setIndexAliasSuffix(indexAliasSuffix);
		esIndexer.setUpdateNewestIndex(update);
		esIndexer.setIndexConfig(indexConfig);
		esIndexer.lookupMabxmlDeletion = Boolean
				.parseBoolean(System.getProperty("lookupMabxmlDeletion", "false"));
		esIndexer.lookupMabxmlDeletion = false;
		esIndexer.lookupWikidata = false;
		esIndexer.onSetReceiver();
		StreamBatchLogger batchLogger = new StreamBatchLogger();
		batchLogger.setBatchSize(100000);
		final StringRecordSplitter srs =
				new StringRecordSplitter(RECORD_SPLITTER_MARKER);
		ObjectBatchLogger<HashMap<String, String>> objectBatchLogger =
				new ObjectBatchLogger<>();
		objectBatchLogger.setBatchSize(500000);
		opener.setReceiver(srs).setReceiver(triple2model).setReceiver(jsonConverter)
				.setReceiver(objectBatchLogger).setReceiver(esIndexer);
		opener.process(new File(inputPath).getAbsolutePath());
		opener.closeStream();
	}
}
