/* Copyright 2019 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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

import de.hbz.lobid.helper.Email;

/**
 * Useful after a complete ETL to switch the 'staging' index with the alias of
 * production. Makes the staging index productive (if it's the newer one) and
 * the productive index aliased to 'staging'. If a sanity check is successful
 * the index aliases "[resources|geo_nwbib]-staging" are updated to
 * "[resources|geo_nwbib]" and vice versa. Also writes an email using
 * {@link de.hbz.lobid.helper.Email} to write an email to the address given by
 * the java system property 'emailTo'.
 * 
 * @author Pascal Christoph (dr0i)
 *
 */
public class UpdateAliases {
	private static ObjectMapper objectMapper = new ObjectMapper();
	private static final Logger LOG = LogManager.getLogger(UpdateAliases.class);
	private static boolean success = false;
	private static String logMessage;
	private static StringBuilder logMessages = new StringBuilder(512);

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
			success = true;
		} catch (Exception | AssertionError e) {
			e.printStackTrace();
			logMessage = "\nAlias switching failed!";
			LOG.error(logMessage);
			logMessages.append(logMessage);
			success = false;
		}
		String emailTo = System.getProperty("emailTo");
		if (emailTo != null)
			Email.sendEmail("hduser", emailTo,
					"Alias switching " + (success ? "success :)" : "fails :("),
					logMessages.toString());
		else
			LOG.warn("Please provide 'emailTo' as system property to enable mailing");
	}

	private static void testIndexSanity() throws IOException {
		docsCountTest();
		indexSizeTest();
		spatialNwbibCountTest();
		if (System.getProperties().contains("deletedCountTest"))
			deletedCountTest();
	}

	private static JsonNode queryEsAndGetJNode(final String QUERY)
			throws IOException {
		JsonNode node = null;
		URL url = new URL("http://weywot4.hbz-nrw.de:9200/" + QUERY);
		node = objectMapper.readTree(url);
		return node;
	}

	private static void docsCountTest() throws IOException {
		int allPrimariesDocsCountStaging =
				queryEsAndGetJNode("resources-staging/_stats")
						.at("/_all/primaries/docs/count").asInt();
		Assert.assertTrue(allPrimariesDocsCountStaging > 139000000);

	}

	private static void indexSizeTest() throws IOException {
		long sizeInBytes = queryEsAndGetJNode("resources-staging/_stats")
				.at("/_all/primaries/store/size_in_bytes").asLong();
		Assert.assertTrue(sizeInBytes > 77 * 1024 * 1024);
	}

	private static void spatialNwbibCountTest() throws IOException {
		long count =
				queryEsAndGetJNode("resources-staging/_search?q=_exists_%3Aspatial.id")
						.at("/hits/total").asLong();
		Assert.assertTrue(count > 310000);
	}

	private static void deletedCountTest() throws IOException {
		String creationDateResourcesStaging =
				queryEsAndGetJNode("resources-staging/_settings")
						.findPath("provided_name").asText().split("-")[1];
		final String creationDateResourcesStagingMinusOneWeek = LocalDate
				.parse(creationDateResourcesStaging, DateTimeFormatter.BASIC_ISO_DATE)
				.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE);
		final int totalHitsOldIndex =
				queryEsAndGetJNode("resources/resource/_search?q=*").at("/hits/total")
						.asInt();
		final int totalHitsNewIndex =
				queryEsAndGetJNode("resources-staging/resource/_search?q=*")
						.at("/hits/total").asInt();
		final int differenceOfTotalHitsBetweenOldAndNew =
				totalHitsOldIndex - totalHitsNewIndex;
		logMessage =
				"Difference between the 'just created index minus one week (aka 'production'), that should be created at "
						+ creationDateResourcesStagingMinusOneWeek
						+ ")' and the 'just created index (aka 'staging', created at"
						+ creationDateResourcesStaging + "':"
						+ differenceOfTotalHitsBetweenOldAndNew;
		log(logMessage);
		JsonNode node =
				queryEsAndGetJNode("deletions/_search?q=describedBy.deleted%3A["
						+ creationDateResourcesStagingMinusOneWeek + "+TO+"
						+ creationDateResourcesStaging + "]");
		int deletionsCount = node.at("/hits/total").asInt();
		logMessage = ("Amount of deletions between "
				+ creationDateResourcesStagingMinusOneWeek + " and "
				+ creationDateResourcesStaging + " according to the 'deletion index': "
				+ deletionsCount);
		log(logMessage);
		final int tolerance = (deletionsCount + 1) / 333;
		logMessage = ("Going to compare if " + differenceOfTotalHitsBetweenOldAndNew
				+ " is less or equals " + deletionsCount
				+ " allowing a 0.3% tolerance (i.e. " + tolerance + ")");
		log(logMessage);
		Assert.assertTrue(
				differenceOfTotalHitsBetweenOldAndNew - deletionsCount < tolerance);
	}

	private static void log(final String msg) {
		LOG.info(msg);
		logMessages.append("\n" + msg);
	}
}
