package controllers.resources;

import java.net.InetAddress;
import java.util.function.Consumer;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilders;

import com.fasterxml.jackson.databind.JsonNode;

import play.libs.Json;

/**
 * Access to the Elasticsearch index.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Index {

	private static final String INDEX_NAME =
			Application.CONFIG.getString("index.name");
	private static final String TYPE_ITEM =
			Application.CONFIG.getString("index.type.item");
	private static final String TYPE_RESOURCE =
			Application.CONFIG.getString("index.type.resource");
	private static final int CLUSTER_PORT =
			Application.CONFIG.getInt("index.cluster.port");
	private static final String CLUSTER_HOST =
			Application.CONFIG.getString("index.cluster.host");
	private static final String CLUSTER_NAME =
			Application.CONFIG.getString("index.cluster.name");

	private JsonNode result;
	private long total = 0;

	/**
	 * @param q The string to use for an Elasticsearch queryStringQuery
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Index queryResources(String q) {
		return withClient((Client client) -> {
			SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX_NAME)
					.setTypes(TYPE_RESOURCE).setQuery(QueryBuilders.queryStringQuery(q));
			SearchResponse response = requestBuilder.execute().actionGet();
			result = Json.parse(response.toString()).get("hits").get("hits");
			total = response.getHits().getTotalHits();
		});
	}

	/**
	 * @param id The item ID to use for an Elasticsearch GET request
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Index getItem(String id) {
		return withClient((Client client) -> {
			String sourceAsString = client.prepareGet(INDEX_NAME, TYPE_ITEM, id)
					.setParent(id.split(":")[0]).execute().actionGet()
					.getSourceAsString();
			result = Json.parse(sourceAsString);
			total = 1;
		});
	}

	/**
	 * @return The index result for the query (the hits) or GET (single result)
	 */
	public JsonNode getResult() {
		return result;
	}

	/**
	 * @return The total hits count for the last query or GET
	 */
	public long getTotal() {
		return total;
	}

	private Index withClient(Consumer<Client> method) {
		Settings settings =
				Settings.settingsBuilder().put("cluster.name", CLUSTER_NAME).build();
		try (Client client = TransportClient.builder().settings(settings).build()
				.addTransportAddress(new InetSocketTransportAddress(
						InetAddress.getByName(CLUSTER_HOST), CLUSTER_PORT))) {
			method.accept(client);
			return this;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
