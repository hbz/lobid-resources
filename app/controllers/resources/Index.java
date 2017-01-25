package controllers.resources;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import com.fasterxml.jackson.databind.JsonNode;

import play.Logger;
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
	private static final List<String> CLUSTER_HOSTS =
			Application.CONFIG.getList("index.cluster.hosts").stream()
					.map(v -> v.unwrapped().toString()).collect(Collectors.toList());
	private static final String CLUSTER_NAME =
			Application.CONFIG.getString("index.cluster.name");

	private JsonNode result;
	private long total = 0;
	private JsonNode aggregations;

	/**
	 * @param q The string to use for an Elasticsearch queryStringQuery
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Index queryResources(String q) {
		return queryResources(q, 0, 10, "");
	}

	/**
	 * @param q The string to use for an Elasticsearch queryStringQuery
	 * @param from The from index for the page
	 * @param size The page size, starting at from
	 * @param sort "newest", "oldest", or "" for relevance
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Index queryResources(String q, int from, int size, String sort) {
		Logger.trace("queryResources: q={}, from={}, size={}, sort={}", q, from,
				size, sort);
		return withClient((Client client) -> {
			SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX_NAME)
					.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
					.setTypes(TYPE_RESOURCE).setQuery(QueryBuilders.queryStringQuery(q))
					.setFrom(from).setSize(size);
			if (!sort.isEmpty()) {
				requestBuilder.addSort(SortBuilders.fieldSort("publication.startDate")
						.order(sort.equals("newest") ? SortOrder.DESC : SortOrder.ASC));
			}
			requestBuilder = withAggregations(requestBuilder, "publication.startDate",
					"subject.id", "type", "medium.id", "exemplar.id");
			SearchResponse response = requestBuilder.execute().actionGet();
			SearchHits hits = response.getHits();
			List<JsonNode> results = new ArrayList<>();
			// TODO use Aggregation objects directly, don't parse into JSON
			aggregations = Json.parse(response.toString()).get("aggregations");
			for (SearchHit sh : hits.getHits()) {
				results.add(Json.toJson(sh.getSource()));
			}
			result = Json.toJson(results);
			total = hits.getTotalHits();
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
	 * @param id The resource ID to use for an Elasticsearch GET request
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Index getResource(String id) {
		return withClient((Client client) -> {
			String sourceAsString = client.prepareGet(INDEX_NAME, TYPE_RESOURCE, id)
					.execute().actionGet().getSourceAsString();
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
	 * @return The aggregations for the query
	 */
	public JsonNode getAggregations() {
		return aggregations;
	}

	/**
	 * @return The total hits count for the last query or GET
	 */
	public long getTotal() {
		return total;
	}

	private static SearchRequestBuilder withAggregations(
			final SearchRequestBuilder searchRequest, String... fields) {
		Arrays.asList(fields).forEach(field -> {
			searchRequest.addAggregation(
					AggregationBuilders.terms(field).field(field).size(100)
			// TODO: very high count is required for owners only?
			/* .size(Integer.MAX_VALUE) */);
		});
		return searchRequest;
	}

	private Index withClient(Consumer<Client> method) {
		Settings settings =
				Settings.settingsBuilder().put("cluster.name", CLUSTER_NAME).build();
		try (TransportClient client =
				TransportClient.builder().settings(settings).build()) {
			addHosts(client);
			method.accept(client);
			return this;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static void addHosts(TransportClient client) {
		for (String host : CLUSTER_HOSTS) {
			try {
				client.addTransportAddress(new InetSocketTransportAddress(
						InetAddress.getByName(host), CLUSTER_PORT));
			} catch (Exception e) {
				Logger.warn("Could not add host {} to Elasticsearch client: {}", host,
						e.getMessage());
			}
		}
	}

}
