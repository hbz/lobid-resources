/* Copyright 2019 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.net.InetAddress;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.Assert;
import org.lobid.resources.ElasticsearchIndexer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * If a sanity check is succesful the index aliases
 * "[resources|geo_nwbib]-staging" are updated to "[resources|geo_nwbib]" and
 * vice versa. I.e. making the staging index productive and the productive index
 * to be stage. Before this is a
 * 
 * @author Pascal Christoph (dr0i)
 *
 */
public class UpdateAliases {
	private static ObjectMapper objectMapper = new ObjectMapper();
	private static final Logger LOG = LogManager.getLogger(UpdateAliases.class);

	/**
	 * @param args ignored
	 */
	public static void main(String... args) {
		try (
				TransportClient tc = new PreBuiltTransportClient(
						Settings.builder().put("cluster.name", "weywot").build());
				Client client = tc.addTransportAddress(new InetSocketTransportAddress(
						InetAddress.getByName("weywot4.hbz-nrw.de"), 9300))) {
			ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
			testIndexSanity();
			esIndexer.setElasticsearchClient(client);
			esIndexer.swapProductionAndStagingAliases("resources",
					"resources-staging");
			esIndexer.swapProductionAndStagingAliases("geo_nwbib",
					"geo_nwbib-staging");
		} catch (Exception | AssertionError e) {
			e.printStackTrace();
			LOG.error("Alias switching failed!");
		}
	}

	private static void testIndexSanity() {
		docsCountTest();
		indexSizeTest();
		spatialNwbibCountTest();
		if (System.getProperties().contains("deletedCountTest"))
			deletedCountTest();
	}

	private static JsonNode queryEsAndGetJNode(final String QUERY) {
		JsonNode node = null;
		try {
			URL url = new URL("http://weywot4.hbz-nrw.de:9200/" + QUERY);
			node = objectMapper.readTree(url);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return node;
	}

	private static void docsCountTest() {
		int allPrimariesDocsCountStaging =
				queryEsAndGetJNode("resources-staging/_stats")
						.at("/_all/primaries/docs/count").asInt();
		Assert.assertTrue(allPrimariesDocsCountStaging > 139000000);
	}

	private static void indexSizeTest() {
		long sizeInBytes = queryEsAndGetJNode("resources-staging/_stats")
				.at("/_all/primaries/store/size_in_bytes").asLong();
		Assert.assertTrue(sizeInBytes > 77 * 1024 * 1024);
	}

	private static void spatialNwbibCountTest() {
		long count =
				queryEsAndGetJNode("resources-staging/_search?q=_exists_%3Aspatial.id")
						.at("/hits/total").asLong();
		Assert.assertTrue(count > 310000);
	}

	private static void deletedCountTest() {
		String creationDateResources = queryEsAndGetJNode("resources/_settings")
				.findPath("provided_name").asText().split("-")[1];
		String creationDateResourcesStaging =
				queryEsAndGetJNode("resources-staging/_settings")
						.findPath("provided_name").asText().split("-")[1];
		int resourcesCount = queryEsAndGetJNode("resources/resource/_search?q=*")
				.at("/hits/total").asInt();
		int resourcesStagingCount =
				queryEsAndGetJNode("resources-staging/resource/_search?q=*")
						.at("/hits/total").asInt();
		int differenceDocsCountStagingVsProduction =
				resourcesCount - resourcesStagingCount;
		LOG.info("Difference between production (" + resourcesCount
				+ ") and staging:  (" + resourcesStagingCount + ") :"
				+ differenceDocsCountStagingVsProduction);
		JsonNode node = queryEsAndGetJNode(
				"deletions/_search?q=describedBy.deleted%3A[" + creationDateResources
						+ "+TO+" + creationDateResourcesStaging + "]");
		int deletionsCount = node.at("/hits/total").asInt();
		LOG.info("Amount of deletions between " + creationDateResources + " and "
				+ creationDateResourcesStaging + ": " + deletionsCount);
		LOG.info("Going to compare if " + differenceDocsCountStagingVsProduction
				+ " equals " + deletionsCount);
		Assert.assertTrue(differenceDocsCountStagingVsProduction == deletionsCount);
	}
}
