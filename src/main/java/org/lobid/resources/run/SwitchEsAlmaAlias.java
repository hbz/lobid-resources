/* Copyright 2021 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.Assert;
import org.lobid.resources.ElasticsearchIndexer;

/**
 * Useful after a complete ETL to switch the 'staging' index with the alias of
 * production. Makes the staging index productive (if it's the newer one) and
 * the productive index aliased to 'staging'. If a sanity check is successful
 * the index alias "almaresources-staging" is switched to the alias name
 * "almaresources" and vice versa.
 *
 * @author Pascal Christoph (dr0i)
 *
 */
public final class SwitchEsAlmaAlias {
  private static final int DOCS_MINIMUM = 83000000;
  private static final int BYTES_MINIMUM = 49 * 1024 * 1024 * 1024;
  private static ObjectMapper objectMapper = new ObjectMapper();
  private static final Logger LOG = LoggerFactory.getLogger(SwitchEsAlmaAlias.class);
  private static StringBuilder logMessages = new StringBuilder(512);

  private SwitchEsAlmaAlias() {
    // not called
  }

  /**
   * @param ALIAS1  the alias to be switched with alias2
   * @param ALIAS2  the alias to be switched with alias1
   * @param ES_NODE the elasticsearch node where the indices can be reached
   * @param MINDOCS the alias to be switched has to have at least so many docs
   * @param MINSIZE the alias to be switched has to have at least this size in
   *                  bytes
   * @return success status
   * @throws UnknownHostException if host unknow
   */
  public static boolean switchAlias(final String ALIAS1, final String ALIAS2,
      final String ES_NODE, final String MINDOCS, final String MINSIZE)
      throws UnknownHostException {
    boolean success = false;
    String logMessage = "";
    try (
        TransportClient tc = new PreBuiltTransportClient(
            Settings.builder().put("cluster.name", "weywot").build());
        Client client = tc.addTransportAddress(new InetSocketTransportAddress(
            InetAddress.getByName(ES_NODE), 9300))) {
      ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
      testIndexSanity(ALIAS2, ES_NODE, MINDOCS, MINSIZE);
      esIndexer.setElasticsearchClient(client);
      esIndexer.swapProductionAndStagingAliases(ALIAS1, ALIAS2);
      success = true;
    } catch (Exception | AssertionError e) {
      logMessage = "Alias switching failed!" + e.toString();
      LOG.error(logMessage);
      logMessages.append(logMessage);
      success = false;
    }
    return success;
  }

  private static void testIndexSanity(final String ALIAS2, final String ES_NODE,
      final String MINDOCS, final String MINSIZE) throws IOException {
    docsCountTest(ALIAS2, ES_NODE, MINDOCS);
    indexSizeTest(ALIAS2, ES_NODE, MINSIZE);
  }

  private static void docsCountTest(final String ALIAS2, final String ES_NODE,
      final String MINDOCS) throws IOException {
    int allPrimariesDocsCountStaging =
        queryEsAndGetJNode(ES_NODE, ALIAS2 + "/_stats")
            .at("/_all/primaries/docs/count").asInt();
    Assert.assertTrue(allPrimariesDocsCountStaging > Integer.valueOf(MINDOCS));

  }

  private static void indexSizeTest(final String ALIAS2, final String ES_NODE,
      final String MINSIZE) throws IOException {
    long sizeInBytes = queryEsAndGetJNode(ES_NODE, ALIAS2 + "/_stats")
        .at("/_all/primaries/store/size_in_bytes").asLong();
    Assert.assertTrue(sizeInBytes > Long.valueOf(MINSIZE));
  }

  private static JsonNode queryEsAndGetJNode(final String ES_NODE,
      final String QUERY) throws IOException {
    JsonNode node = null;
    URL url = new URL("http://" + ES_NODE + ":9200/" + QUERY);
    node = objectMapper.readTree(url);
    return node;
  }
}
