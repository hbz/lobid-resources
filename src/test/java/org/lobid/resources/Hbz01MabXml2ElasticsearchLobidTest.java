/* Copyright 2015  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.lobid.resources;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.xml.AlephMabXmlHandler;
import org.culturegraph.mf.stream.converter.xml.XmlDecoder;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.stream.source.TarReader;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.search.SearchHit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaTripleCallback;
import com.github.jsonldjava.utils.JSONUtils;
import com.hp.hpl.jena.rdf.model.Model;

/**
 * Transform hbz01 Aleph Mab XML catalog data into lobid elasticsearch JSON-LD.
 * Query the index and test the data by transforming the data into one big
 * ntriple file (which is great to make diffs) and into several JSON-LD files
 * (reflecting the records residing in elasticsearch).
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class Hbz01MabXml2ElasticsearchLobidTest {
	private static Node node;
	static Client client;
	private static final Logger LOG =
			LoggerFactory.getLogger(Hbz01MabXml2ElasticsearchLobidTest.class);
	private static final String LOBID_RESOURCES =
			"test-resources-" + LocalDateTime.now().toLocalDate() + "-"
					+ LocalDateTime.now().toLocalTime();
	private static final String N_TRIPLE = "N-TRIPLE";
	static final String TEST_FILENAME_NTRIPLES = "hbz01.es.nt";

	@BeforeClass
	public static void setup() {
		node = nodeBuilder().local(true)
				.settings(ImmutableSettings.settingsBuilder()
						.put("index.number_of_replicas", "0")
						.put("index.number_of_shards", "1").build())
				.node();
		client = node.client();
		client.admin().indices().prepareDelete("_all").execute().actionGet();
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute()
				.actionGet();
		etl(client, new RdfModel2ElasticsearchEtikettJsonLd());
	}

	/*
	 * ETL stands for extract, transform, load. Extract data from AlephmabXml
	 * clobs, transform into lobid ntriples, transform that into elasticsearch
	 * json-ld, index that into elasticsearch.
	 */
	public static void etl(final Client cl,
			RdfModel2ElasticsearchEtikettJsonLd etikettJsonLdConverter) {
		client = cl;
		final FileOpener opener = new FileOpener();
		final Triples2RdfModel triple2model = new Triples2RdfModel();
		triple2model.setInput(N_TRIPLE);
		opener.setReceiver(new TarReader()).setReceiver(new XmlDecoder())
				.setReceiver(new AlephMabXmlHandler())
				.setReceiver(
						new Metamorph("src/main/resources/morph-hbz01-to-lobid.xml"))
				.setReceiver(new PipeEncodeTriples()).setReceiver(triple2model)
				.setReceiver(etikettJsonLdConverter)
				.setReceiver(getElasticsearchIndexer(cl));
		opener.process(
				new File("src/test/resources/hbz01XmlClobs.tar.bz2").getAbsolutePath());
		opener.closeStream();
	}

	@SuppressWarnings("static-method")
	@Test
	public void testJson() {
		ElasticsearchDocuments.getAsJson();
	}

	@SuppressWarnings("static-method")
	@Test
	public void testNtriples() {
		writeSortedFile(TEST_FILENAME_NTRIPLES,
				ElasticsearchDocuments.getAsNtriples());
		testFiles(TEST_FILENAME_NTRIPLES);
	}

	static void writeSortedFile(final String TEST_FILENAME,
			final String DOCUMENT) {
		File testFile = new File(TEST_FILENAME);
		SortedSet<String> set = new TreeSet<>();
		try (Scanner scanner = new Scanner(DOCUMENT)) {
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				if (!line.isEmpty())
					set.add(line);
			}
		}
		try {
			FileUtils.writeStringToFile(testFile,
					set.parallelStream().collect(Collectors.joining("\n")), false);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static void writeFile(final String TEST_FILENAME, final String DOCUMENT) {
		File testFile = new File(TEST_FILENAME);
		try {
			FileUtils.writeStringToFile(testFile, DOCUMENT, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static void testFiles(final String TEST_FILENAME) {
		File testFile = new File(TEST_FILENAME);
		AbstractIngestTests.compareFilesDefaultingBNodesAndCommata(testFile,
				new File("src/test/resources/" + TEST_FILENAME));
		testFile.deleteOnExit();
	}

	private static ElasticsearchIndexer getElasticsearchIndexer(final Client cl) {
		ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
		esIndexer.setElasticsearchClient(cl);
		esIndexer.setIndexName(LOBID_RESOURCES);
		esIndexer.setIndexAliasSuffix("");
		esIndexer.setUpdateNewestIndex(false);
		esIndexer.onSetReceiver();
		return esIndexer;
	}

	@AfterClass
	public static void down() {
		client.admin().indices().prepareDelete("_all").execute().actionGet();
		node.close();
	}

	static class ElasticsearchDocuments {
		static private SearchResponse getElasticsearchDocuments() {
			return client.prepareSearch(LOBID_RESOURCES)
					.setQuery(new MatchAllQueryBuilder()).setFrom(0).setSize(10000)
					.execute().actionGet();
		}

		static String getAsNtriples() {
			return Arrays.asList(getElasticsearchDocuments().getHits().getHits())
					.parallelStream()
					.flatMap(hit -> Stream.of(toRdf(hit.getSourceAsString())))
					.collect(Collectors.joining());
		}

		static void getAsJson() {
			for (SearchHit hit : getElasticsearchDocuments().getHits().getHits()) {
				stripContextAndSaveAsFile(hit.getSourceAsString());

			}
		}

		/*
		 * As the 'context' is just bloating the content the context is stripped
		 * from it.
		 */
		private static void stripContextAndSaveAsFile(final String jsonLd) {
			String jsonLdWithoutContext = null;
			try {
				Map<String, Object> map =
						new ObjectMapper().readValue(jsonLd, Map.class);
				map.put("@context", AbstractIngestTests.LOBID_JSONLD_CONTEXT);
				jsonLdWithoutContext = new ObjectMapper().defaultPrettyPrintingWriter()
						.writeValueAsString(map);
				String filename =
						((String) map.get("id")).replaceAll("/about", "").replaceAll(
								RdfModel2ElasticsearchEtikettJsonLd.LOBID_DOMAIN + ".*/", "");
				filename = "jsonld/" + filename;
				writeFile(filename, jsonLdWithoutContext);
				testFiles(filename);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private static String toRdf(final String jsonLd) {
			try {
				LOG.debug("toRdf: " + jsonLd);
				final Object jsonObject = JSONUtils.fromString(jsonLd);
				final JenaTripleCallback callback = new JenaTripleCallback();
				final Model model = (Model) JsonLdProcessor.toRDF(jsonObject, callback);
				final StringWriter writer = new StringWriter();
				model.write(writer, N_TRIPLE);
				return writer.toString();
			} catch (IOException | JsonLdError e) {
				e.printStackTrace();
			}
			return null;
		}
	}
}
