/* Copyright 2015, 2018 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.RecordReader;
import org.culturegraph.mf.stream.converter.xml.AlephMabXmlHandler;
import org.culturegraph.mf.stream.converter.xml.XmlDecoder;
import org.culturegraph.mf.stream.pipe.ObjectBatchLogger;
import org.culturegraph.mf.stream.pipe.StreamBatchLogger;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.stream.source.StringReader;
import org.culturegraph.mf.stream.source.TarReader;
import org.lobid.resources.ElasticsearchIndexer;
import org.lobid.resources.JsonLdEtikett;
import org.lobid.resources.JsonLdItemSplitter2ElasticsearchJsonLd;
import org.lobid.resources.ObjectThreader;
import org.lobid.resources.PipeEncodeTriples;
import org.lobid.resources.RdfGraphToJsonLd;

/**
 * Transform hbz01 Aleph Mab XML catalog data into lobid elasticsearch ready
 * JSON-LD and index that into elasticsearch. Using proper parameters the aleph
 * "Loeschsaetze" will be etl'ed into an index of its own.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public class MabXml2lobidJsonEs {
	public static String jsonLdContextUri =
			"http://lobid.org/resources/context.jsonld";
	private static final String MORPH_FN_PREFIX = "src/main/resources/";
	public static final String CONTEXT_URI =
			"http://lobid.org/resources/context.jsonld";
	static RdfGraphToJsonLd rdfGraphToJsonLd = new RdfGraphToJsonLd(CONTEXT_URI);
	private static String indexAliasSuffix;
	private static String node;
	private static String cluster;
	private static String indexName;
	private static String labelsDirectoryName;
	private static boolean updateDonotCreateIndex;
	private static String indexConfig;
	private static boolean lookupMabxmlDeletion;

	public static void main(String... args) {
		String usage =
				"<input path>%s<index name>%s<index alias suffix ('NOALIAS' sets it empty)>%s<node>%s<cluster>%s<'update' (will take latest index), 'exact' (will take ->'index name' even when no timestamp is suffixed) , else create new index with actual timestamp>%s<optional: filename of index-config>%s<optional: filename of morph>%s<optional: jsonld-context-uri>%s";
		String inputPath = args[0];
		System.out.println("inputFile=" + inputPath);
		indexName = args[1];
		String date = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		indexName =
				indexName.matches(".*-20.*") || args[5].toLowerCase().equals("exact")
						? indexName
						: indexName + "-" + date;
		indexAliasSuffix = args[2];
		node = args[3];
		cluster = args[4];
		updateDonotCreateIndex = args[5].toLowerCase().equals("update");
		if (args.length < 6) {
			System.err.println("Usage: MabXml2lobidJsonEs"
					+ String.format(usage, " ", " ", " ", " ", " ", " ", " ", " ", " "));
			return;
		}
		indexConfig = args.length >= 7 ? args[6] : "index-config.json";
		System.out.println("using indexConfig: " + indexConfig);
		String morphFileName = args.length >= 8 ? MORPH_FN_PREFIX + args[7]
				: MORPH_FN_PREFIX + "morph-hbz01-to-lobid.xml";
		System.out.println("using morph: " + morphFileName);
		rdfGraphToJsonLd.setContextLocationFilname(
				System.getProperty("contextFilename", "web/conf/context.jsonld"));
		System.out.println(
				"contextFilename:" + rdfGraphToJsonLd.getContextLocationFilename());
		if (args.length >= 9) {
			rdfGraphToJsonLd.setContextUri(args[8]);
		}
		if (args.length >= 10)
			labelsDirectoryName = args[9];
		System.out
				.println("using jsonLdContextUri: " + rdfGraphToJsonLd.getContextUri());

		// hbz catalog transformation
		final FileOpener opener = new FileOpener();
		if (inputPath.toLowerCase().endsWith("bz2")) {
			opener.setCompression("BZIP2");
		} else if (inputPath.toLowerCase().endsWith("gz"))
			opener.setCompression("GZIP");

		lookupMabxmlDeletion = Boolean
				.parseBoolean(System.getProperty("lookupMabxmlDeletion", "false"));

		opener.setReceiver(new TarReader()).setReceiver(new RecordReader())
				.setReceiver(new ObjectThreader<String>())//
				.addReceiver(receiverThread())//
				.addReceiver(receiverThread())//
				.addReceiver(receiverThread())//
				.addReceiver(receiverThread())//
				.addReceiver(receiverThread())//
				.addReceiver(receiverThread());
		try {
			opener.process(new File(inputPath).getAbsolutePath());
			opener.closeStream();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static ElasticsearchIndexer getElasticsearchIndexer() {
		ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
		esIndexer.setClustername(cluster);
		esIndexer.setHostname(node);
		esIndexer.setIndexName(indexName);
		esIndexer.setIndexAliasSuffix(indexAliasSuffix);
		esIndexer.setUpdateNewestIndex(updateDonotCreateIndex);
		esIndexer.setIndexConfig(indexConfig);
		esIndexer.lookupMabxmlDeletion = lookupMabxmlDeletion;
		System.out
				.println("lookupMabxmlDeletion: " + esIndexer.lookupMabxmlDeletion);
		esIndexer.lookupWikidata =
				Boolean.parseBoolean(esIndexer.lookupMabxmlDeletion ? "false"
						: System.getProperty("lookupWikidata", "true"));
		System.out.println("lookupWikidata: " + esIndexer.lookupWikidata);
		if (esIndexer.lookupMabxmlDeletion)
			esIndexer.lookupWikidata = false;
		esIndexer.onSetReceiver();
		WikidataGeodata2Es.storeIfIndexExists(esIndexer.getElasticsearchClient());
		return esIndexer;
	}

	private static StringReader receiverThread() {
		StreamBatchLogger batchLogger = new StreamBatchLogger();
		batchLogger.setBatchSize(100000);
		JsonLdEtikett jsonLdEtikett;
		if (labelsDirectoryName != null) {
			jsonLdEtikett = new JsonLdEtikett(labelsDirectoryName,
					rdfGraphToJsonLd.getContextLocationFilename());
		} else
			jsonLdEtikett = new JsonLdEtikett();
		final String KEY_TO_GET_MAIN_ID =
				System.getProperty("keyToGetMainId", "hbzId");
		System.out.println("using keyToGetMainId:" + KEY_TO_GET_MAIN_ID);
		System.out.println("using etikettLablesDirectory: "
				+ JsonLdEtikett.getLabelsDirectoryName());
		ObjectBatchLogger<HashMap<String, String>> objectBatchLogger =
				new ObjectBatchLogger<>();
		objectBatchLogger.setBatchSize(500000);
		StringReader sr = new StringReader();
		sr.setReceiver(new XmlDecoder()).setReceiver(new AlephMabXmlHandler())
				.setReceiver(
						new Metamorph("src/main/resources/morph-hbz01-to-lobid.xml"))
				.setReceiver(batchLogger)//
				.setReceiver(new PipeEncodeTriples())//
				.setReceiver(new RdfGraphToJsonLd(MabXml2lobidJsonEs.CONTEXT_URI))//
				.setReceiver(jsonLdEtikett)//
				.setReceiver(
						new JsonLdItemSplitter2ElasticsearchJsonLd(KEY_TO_GET_MAIN_ID))//
				.setReceiver(objectBatchLogger)//
				.setReceiver(getElasticsearchIndexer());
		return sr;
	}
}
