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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.culturegraph.mf.morph.Metamorph;
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
	protected static Client client;

	private static final String LOBID_RESOURCES =
			"hbz01-mabxml2elasticsearch-lobid-test-"
					+ LocalDateTime.now().toLocalDate() + "-"
					+ LocalDateTime.now().toLocalTime();
	private static final String N_TRIPLE = "N-TRIPLE";
	private static final String TEST_FILENAME_NTRIPLES = "hbz01.es.nt";

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
		final FileOpener opener = new FileOpener();
		final Triples2RdfModel triple2model = new Triples2RdfModel();
		triple2model.setInput(N_TRIPLE);
		opener.setReceiver(new TarReader()).setReceiver(new XmlDecoder())
				.setReceiver(new MabXmlHandler())
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
		writeFileAndTest(TEST_FILENAME_NTRIPLES,
				ElasticsearchDocuments.getAsNtriples());
	}

	private static void writeFileAndTest(final String TEST_FILENAME,
			final String DOCUMENTS) {
		File testFile = new File(TEST_FILENAME);
		try {
			FileUtils.writeStringToFile(testFile, DOCUMENTS, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
		AbstractIngestTests.compareFilesDefaultingBNodes(testFile,
				new File("src/test/resources/" + (TEST_FILENAME)));
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

		private static void stripContextAndSaveAsFile(final String jsonLd) {
			String jsonLdWithoutContext = null;
			try {
				Map<String, Object> map =
						new ObjectMapper().readValue(jsonLd, Map.class);
				map.put("@context", AbstractIngestTests.LOBID_JSONLD_CONTEXT);
				jsonLdWithoutContext = new ObjectMapper().defaultPrettyPrintingWriter()
						.writeValueAsString(map);
				String filename =
						((String) map.get("@id")).replaceAll("/about", "").replaceAll(
								RdfModel2ElasticsearchEtikettJsonLd.LOBID_DOMAIN + ".*/", "");
				writeFileAndTest("jsonld/" + filename, jsonLdWithoutContext);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private static String toRdf(final String jsonLd) {
			try {
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
