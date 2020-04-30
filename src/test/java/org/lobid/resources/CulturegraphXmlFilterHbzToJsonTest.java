/* Copyright 2020 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package org.lobid.resources;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lobid.resources.run.CulturegraphXmlFilterHbzToJson;

/**
 * Test of filtering resources with hbz holdings from culturegraph marcxml,
 * tranforming into JSON, writing as an elasticsearch bulk json file, ingesting
 * it and retrieving it via HTTP.
 * 
 * @author Pascal Christoph(dr0i)
 **/
@SuppressWarnings("javadoc")
public final class CulturegraphXmlFilterHbzToJsonTest {

	private static final Logger LOG =
			LogManager.getLogger(CulturegraphXmlFilterHbzToJsonTest.class);

	private static final String PATH_TO_TEST = "src/test/resources/";
	public static final String JSON_OUTPUT_FILE =
			PATH_TO_TEST + "jsonld-cg/bulk.ndjson";

	private static final String XML_INPUT_FILE =
			"/aggregate_auslieferung_20191212.small.marcxml";
	private static PluginConfigurableNode node;
	private static Client client;
	private static final int ELASTICSEARCH_HTTP_PORT = 19200;

	private static final String ELASTICSEARCH_BULK_URI =
			"http://localhost:" + ELASTICSEARCH_HTTP_PORT + "/_bulk";
	private static final String ELASTICSEARCH_TEST_NODE_NAME = "testNodeCgRvk";
	// classToTest = new CulturegraphXmlFilterHbzToJson();

	private static final Collection<Class<? extends Plugin>> plugins =
			Arrays.asList(Netty4Plugin.class);

	private static class PluginConfigurableNode extends Node {
		public PluginConfigurableNode(final Settings settings,
				final Collection<Class<? extends Plugin>> classpathPlugins) {
			super(InternalSettingsPreparer.prepareEnvironment(settings, null),
					classpathPlugins);
		}
	}

	@BeforeClass
	public static void setup() {
		try {
			Files.deleteIfExists(Paths.get(JSON_OUTPUT_FILE));
		} catch (final IOException e) {
			e.printStackTrace();
		}

		node = new PluginConfigurableNode(Settings.builder()
				.put(Node.NODE_NAME_SETTING.getKey(),
						ELASTICSEARCH_TEST_NODE_NAME)
				.put(NetworkModule.TRANSPORT_TYPE_KEY,
						NetworkModule.LOCAL_TRANSPORT)
				.put("http.enabled", "true").put("path.home", "tmp")
				.put("transport.type", "netty4").put("network.host", "_local_")
				.put("transport.tcp.port", ELASTICSEARCH_HTTP_PORT + 1)
				.put("http.port", ELASTICSEARCH_HTTP_PORT)
				.put("discovery.type", "single-node").build(), plugins);
		try {
			node.start();
			client = node.client();
			client.admin().indices().prepareDelete("_all").execute()
					.actionGet();
			client.admin().cluster().prepareHealth().setWaitForYellowStatus()
					.execute().actionGet();
			LOG.info(
					"Start extraction, transformation and creation of json bulk ... ");
			etl();
			LOG.info(
					"Done extraction, transformation and creation of json bulk");
		} catch (NodeValidationException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Extract and transform
	 */
	private static void etl() {
		CulturegraphXmlFilterHbzToJson.main(PATH_TO_TEST + XML_INPUT_FILE,
				JSON_OUTPUT_FILE);
	}

	@SuppressWarnings("static-method")
	@Test
	public void testIngestJsonBulkIntoElasticsearch() {
		try {
			ingest();
		} catch (final Exception e) {
			LOG.error(e.getMessage());
		}
	}

	// TODO: create and use metafacture modul "http-writer"
	private static void ingest() throws IOException {
		File jsonFile = new File(JSON_OUTPUT_FILE);
		HttpEntity entity = new FileEntity(jsonFile);
		HttpPost post = new HttpPost(ELASTICSEARCH_BULK_URI);
		post.setEntity(entity);
		post.addHeader("Content-Type", "application/x-ndjson");
		HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		try (CloseableHttpClient httpclient = clientBuilder.build();
				CloseableHttpResponse response = httpclient.execute(post)) {
			assertEquals(response.getStatusLine().getStatusCode(), 200);
		}
	}

	/*
	 * Tears down the elasticsearch test instance.
	 */
	@AfterClass
	public static void down() {
		client.admin().indices().prepareDelete("_all").execute().actionGet();
		try {
			node.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
