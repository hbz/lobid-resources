/* Copyright 2015, 2018 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.xml.AlephMabXmlHandler;
import org.culturegraph.mf.stream.converter.xml.XmlDecoder;
import org.culturegraph.mf.stream.pipe.ObjectBatchLogger;
import org.culturegraph.mf.stream.pipe.StreamBatchLogger;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.stream.source.TarReader;
import org.lobid.resources.ElasticsearchIndexer;
import org.lobid.resources.PipeEncodeTriples;
import org.lobid.resources.RdfModel2ElasticsearchEtikettJsonLd;
import org.lobid.resources.Triples2RdfModel;

import com.hp.hpl.jena.rdf.model.Model;

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
	public static String jsonLdContext =
			"http://lobid.org/resources/context.jsonld";
	private static final String MORPH_FN_PREFIX = "src/main/resources/";
	private static RdfModel2ElasticsearchEtikettJsonLd model2json =
			new RdfModel2ElasticsearchEtikettJsonLd();

	public static void main(String... args) {
		String usage =
				"<input path>%s<index name>%s<index alias suffix ('NOALIAS' sets it empty)>%s<node>%s<cluster>%s<'update' (will take latest index), 'exact' (will take ->'index name' even when no timestamp is suffixed) , else create new index with actual timestamp>%s<optional: filename of index-config>%s<optional: filename of morph>%s<optional: jsonld-context-uri>%s";
		String inputPath = args[0];
		String indexName = args[1];
		String date = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		indexName =
				indexName.matches(".*-20.*") || args[5].toLowerCase().equals("exact")
						? indexName : indexName + "-" + date;
		String indexAliasSuffix = args[2];
		String node = args[3];
		String cluster = args[4];
		String updateCreateExact = args[5];
		boolean update = args[5].toLowerCase().equals("update");
		if (args.length < 6) {
			System.err.println("Usage: MabXml2lobidJsonEs"
					+ String.format(usage, " ", " ", " ", " ", " ", " ", " ", " ", " "));
			return;
		}
		String indexConfig = args.length >= 7 ? args[6] : "index-config.json";
		System.out.println("using indexConfig: " + indexConfig);
		String morphFileName = args.length >= 8 ? MORPH_FN_PREFIX + args[7]
				: MORPH_FN_PREFIX + "morph-hbz01-to-lobid.xml";
		System.out.println("using morph: " + morphFileName);
		if (args.length >= 9)
			jsonLdContext = args[8];
		if (args.length >= 10)
			model2json = new RdfModel2ElasticsearchEtikettJsonLd(new File(args[9]),
					jsonLdContext);
		else
			model2json = new RdfModel2ElasticsearchEtikettJsonLd(
					MabXml2lobidJsonEs.jsonLdContext);
		System.out.println("using jsonLdContext: " + model2json.getJsonLdContext());
		System.out.println(
				"using jsonLdDirectory: " + model2json.getLabelsDirectoryName());
		model2json.getLabelsDirectoryName();
		if (args.length >= 11) {
			model2json.setRootIdPredicate(args[10]);
		}
		System.out.println(
				"It is specified:\n" + String.format(usage, ": " + inputPath + "\n",
						": " + indexName + "\n", ": " + indexAliasSuffix + "\n",
						": " + node + "\n", ": " + cluster + "\n",
						": " + updateCreateExact + "\n", ": " + indexConfig + "\n",
						": " + morphFileName + "\n", ": " + jsonLdContext));
		System.out
				.println("using rootIdPredicate: " + model2json.getRootIdPredicate());
		DefaultObjectPipe<Model, ObjectReceiver<HashMap<String, String>>> jsonConverter =
				model2json;
		// hbz catalog transformation
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
		StreamBatchLogger batchLogger = new StreamBatchLogger();
		batchLogger.setBatchSize(100000);
		ObjectBatchLogger<HashMap<String, String>> objectBatchLogger =
				new ObjectBatchLogger<>();
		objectBatchLogger.setBatchSize(500000);
		batchLogger.setReceiver(new PipeEncodeTriples()).setReceiver(triple2model)
				.setReceiver(jsonConverter).setReceiver(objectBatchLogger)
				.setReceiver(esIndexer);
		opener.setReceiver(new TarReader()).setReceiver(new XmlDecoder())
				.setReceiver(new AlephMabXmlHandler())
				.setReceiver(new Metamorph(morphFileName)).setReceiver(batchLogger);
		opener.process(new File(inputPath).getAbsolutePath());
		opener.closeStream();
	}

	public RdfModel2ElasticsearchEtikettJsonLd getRdfModel2ElasticsearchEtikettJsonLd() {
		return model2json;
	}
}
