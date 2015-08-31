/* Copyright 2015  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.lobid.resources;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.culturegraph.mf.stream.converter.RecordReader;
import org.culturegraph.mf.stream.source.DirReader;
import org.culturegraph.mf.stream.source.FileOpener;
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
 * Reads a directory with records got from lobid.org in ntriple serialization.
 * The records are indexed as JSON-LD in an in-memory elasticsearch instance,
 * then queried and concatenated into two files:
 * <ul>
 * <li>one json file, reflecting the source field of elasticsearch
 * <li>one ntriple file, which is great to make diffs upon
 * </ul>
 * For testing, diffs are done against these files.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class LobidResources2ElasticsearchLobidTest {
	private static Node node;
	protected static Client client;

	private static final String LOBID_RESOURCES = "lobid-resources";
	private static final String N_TRIPLE = "N-TRIPLE";
	private static final String TEST_FILENAME_NTRIPLES = "hbz01.es.nt";
	private static final String TEST_FILENAME_JSON = "hbz01.es.json";

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
		buildAndExecuteFlow(client);
	}

	@SuppressWarnings("static-method")
	@Test
	public void testJson() {
		writeFileAndTest(TEST_FILENAME_JSON, ElasticsearchDocuments.getAsJson());
		writeFileAndTest(TEST_FILENAME_NTRIPLES,
				ElasticsearchDocuments.getAsNtriples());
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
			AbstractIngestTests.compareFilesDefaultingBNodes(testFile,
					new File(Thread.currentThread().getContextClassLoader()
							.getResource(TEST_FILENAME).toURI()));

		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
		testFile.deleteOnExit();
	}

	public static void buildAndExecuteFlow(final Client cl) {
		final DirReader dirReader = new DirReader();
		final FileOpener opener = new FileOpener();
		final Triples2RdfModel triple2model = new Triples2RdfModel();
		triple2model.setInput(N_TRIPLE);
		RecordReader lr = new RecordReader();
		dirReader.setReceiver(opener).setReceiver(lr).setReceiver(triple2model)
				.setReceiver(new RdfModel2ElasticsearchJsonLd())
				.setReceiver(getElasticsearchIndexer(cl));
		dirReader
				.process(new File("src/test/resources/hbz01Records").getAbsolutePath());
		opener.closeStream();
		dirReader.closeStream();
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

		static String getAsJson() {
			return Arrays.asList(getElasticsearchDocuments().getHits().getHits())
					.stream().map(SearchHit::getSourceAsString)
					.collect(Collectors.joining(",\n", "[\n", "\n]"));
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
