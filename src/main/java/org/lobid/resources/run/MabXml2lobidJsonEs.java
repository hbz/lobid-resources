/* Copyright 2015  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
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
import org.culturegraph.mf.stream.pipe.StreamTee;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.stream.source.TarReader;
import org.lobid.resources.ElasticsearchIndexer;
import org.lobid.resources.PipeEncodeTriples;
import org.lobid.resources.RdfModel2ElasticsearchEtikettJsonLd;
import org.lobid.resources.Stats;
import org.lobid.resources.Triples2RdfModel;

import com.hp.hpl.jena.rdf.model.Model;

/**
 * Transform hbz01 Aleph Mab XML catalog data into lobid elasticsearch ready
 * JSON-LD and index that into elasticsearch.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class MabXml2lobidJsonEs {
	public final static String LOBID_RESOURCES_JSONLD_CONTEXT =
			"http://lobid.org/resources/context.jsonld";

	public static void main(String... args) {
		String usage =
				"<input path>%s<index name>%s<index alias suffix>%s<node>%s<cluster>%s<'update' (will take latest index), 'exact' (will take ->'index name' even when no timestamp is suffixed) , else create new index with actual timestamp>%s";
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
		System.out.println(
				"It is specified:\n" + String.format(usage, ": " + inputPath + "\n",
						": " + indexName + "\n", ": " + indexAliasSuffix + "\n",
						": " + node + "\n", ": " + cluster, ": " + "\n" + update));
		if (args.length != 6) {
			System.err.println("Usage: MabXml2lobidJsonEs"
					+ String.format(usage, " ", " ", " ", " ", " ", " "));
			return;
		}
		String jsonLdContext =
				System.getProperty("jsonLdContext", LOBID_RESOURCES_JSONLD_CONTEXT);
		System.out.println("using jsonLdContext: " + jsonLdContext);
		DefaultObjectPipe<Model, ObjectReceiver<HashMap<String, String>>> jsonConverter =
				new RdfModel2ElasticsearchEtikettJsonLd(jsonLdContext);
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
		esIndexer.onSetReceiver();
		StreamBatchLogger batchLogger = new StreamBatchLogger();
		batchLogger.setBatchSize(100000);
		ObjectBatchLogger<HashMap<String, String>> objectBatchLogger =
				new ObjectBatchLogger<>();
		objectBatchLogger.setBatchSize(500000);
		StreamTee streamTee = new StreamTee();
		final Stats stats = new Stats();
		streamTee.addReceiver(stats);
		streamTee.addReceiver(batchLogger);
		batchLogger.setReceiver(new PipeEncodeTriples()).setReceiver(triple2model)
				.setReceiver(jsonConverter).setReceiver(objectBatchLogger)
				.setReceiver(esIndexer);
		opener.setReceiver(new TarReader()).setReceiver(new XmlDecoder())
				.setReceiver(new AlephMabXmlHandler())
				.setReceiver(
						new Metamorph("src/main/resources/morph-hbz01-to-lobid.xml"))
				.setReceiver(streamTee);
		opener.process(new File(inputPath).getAbsolutePath());
		opener.closeStream();
	}
}
