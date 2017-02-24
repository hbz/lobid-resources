package controllers.resources;

import static org.elasticsearch.index.query.QueryBuilders.hasChildQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.children.ChildrenBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import com.fasterxml.jackson.databind.JsonNode;

import play.Logger;
import play.cache.Cache;
import play.libs.Json;

/**
 * Access to the Elasticsearch index.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Index {

	static final String INDEX_NAME = Application.CONFIG.getString("index.name");
	private static final String TYPE_ITEM =
			Application.CONFIG.getString("index.type.item");
	static final String TYPE_RESOURCE =
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
	private Aggregations aggregations;

	/**
	 * The client to use. If null, create default client from settings.
	 */
	public static Client elasticsearchClient = null;

	/**
	 * Fields used when building query strings vis
	 * {@link #buildQueryString(String, String...)}
	 */
	public static final String[] FIELDS =
			new String[] { "contribution.agent.label", "title",
					"subject.id|subject.label", "isbn|issn", "publication.publishedBy",
					"publication.startDate", "medium.id", "type", "collectedBy.id" };

	/**
	 * The values supported for the `aggregations` query parameter.
	 */
	public static final List<String> SUPPORTED_AGGREGATIONS = Arrays.asList(
			"publication.startDate", "subject.id", "type", "medium.id", "owner");

	/**
	 * @param q The current query string
	 * @param values The values corresponding to {@link #FIELDS}
	 * @return A query string created from q, expanded for values
	 */
	public String buildQueryString(String q, String... values) {
		String fullQuery = q.isEmpty() ? "*" : q;
		for (int i = 0; i < values.length; i++) {
			String fieldValue = values[i];
			String fieldName = fieldValue.contains("http")
					? FIELDS[i].replace(".label", ".id") : FIELDS[i];
			if (fieldName.toLowerCase().endsWith("date")
					&& fieldValue.matches("(\\d{1,4}|\\*)-(\\d{1,4}|\\*)")) {
				String[] fromTo = fieldValue.split("-");
				fieldValue = String.format("[%s TO %s]", fromTo[0], fromTo[1]);
			}
			if (!fieldValue.isEmpty()) {
				fullQuery += " AND (" + buildFieldQuery(fieldValue, fieldName) + ")";
			}
		}
		return fullQuery;
	}

	private static String buildFieldQuery(String fieldValue, String fieldName) {
		String q = "";
		String[] fields = fieldName.split("\\|");
		for (int i = 0; i < fields.length; i++) {
			String f = fields[i];
			if (i > 0)
				q += " OR (";
			String[] vals = fieldValue.replace(",AND", "").split(",");
			for (int j = 0; j < vals.length; j++) {
				String v = vals[j];
				q += f + ":"
						+ (f.endsWith(".id") ? "\"" + Lobid.escapeUri(v) + "\"" : v);
				if (j < vals.length - 1)
					q += " AND ";
			}
			if (i > 0)
				q += ")";
		}
		return q;
	}

	/**
	 * @param q The string to use for an Elasticsearch queryStringQuery
	 * @return The number of result for the given query string
	 */
	public long totalHits(String q) {
		try {
			return Cache.getOrElse("total-" + q,
					() -> queryResources(q, 0, 0, "", "", "").getTotal(),
					Application.ONE_DAY);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

	/**
	 * @param q The string to use for an Elasticsearch queryStringQuery
	 * @param from The from index for the page
	 * @param size The page size, starting at from
	 * @param sort "newest", "oldest", or "" for relevance
	 * @param owner Owner institution
	 * @param aggregations The comma separated aggregation fields
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Index queryResources(String q, int from, int size, String sort,
			String owner, @SuppressWarnings("hiding") String aggregations) {
		Index resultIndex = withClient((Client client) -> {
			QueryBuilder query = owner.isEmpty() ? QueryBuilders.queryStringQuery(q)
					: ownerQuery(q, owner);
			validate(client, query);
			Logger.trace("queryResources: q={}, from={}, size={}, sort={}, query={}",
					q, from, size, sort, query);
			SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX_NAME)
					.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
					.setTypes(TYPE_RESOURCE).setQuery(query).setFrom(from).setSize(size);
			if (!sort.isEmpty()) {
				requestBuilder.addSort(SortBuilders.fieldSort("publication.startDate")
						.order(sort.equals("newest") ? SortOrder.DESC : SortOrder.ASC));
			}
			if (!aggregations.isEmpty()) {
				requestBuilder =
						withAggregations(requestBuilder, aggregations.split(","));
			}
			SearchResponse response;
			response = requestBuilder.execute().actionGet();
			SearchHits hits = response.getHits();
			List<JsonNode> results = new ArrayList<>();
			this.aggregations = response.getAggregations();
			for (SearchHit sh : hits.getHits()) {
				results.add(Json.toJson(sh.getSource()));
			}
			result = Json.toJson(results);
			total = hits.getTotalHits();
			return this;
		});
		return resultIndex;
	}

	private static void validate(Client client, QueryBuilder query) {
		ValidateQueryResponse validate =
				client.admin().indices().prepareValidateQuery(INDEX_NAME)
						.setTypes(TYPE_RESOURCE).setQuery(query).get();
		if (!validate.isValid()) {
			throw new IllegalArgumentException("Invalid query: " + query);
		}
	}

	private static QueryBuilder ownerQuery(String q, String owner) {
		final String prefix = Lobid.ORGS_BETA_ROOT;
		BoolQueryBuilder ownersQuery = QueryBuilders.boolQuery();
		final String[] owners = owner.split(",");
		for (String o : owners) {
			final String ownerId = prefix + o.replace(prefix, "");
			ownersQuery = ownersQuery
					.should(hasChildQuery("item", matchQuery("owner.id", ownerId)));
		}
		return QueryBuilders.boolQuery().must(QueryBuilders.queryStringQuery(q))
				.must(ownersQuery);
	}

	/**
	 * @param id The item ID to use for an Elasticsearch GET request
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Index getItem(String id) {
		return withClient((Client client) -> {
			GetResponse response = client.prepareGet(INDEX_NAME, TYPE_ITEM, id)
					.setParent(id.split(":")[0]).execute().actionGet();
			if (response.isExists()) {
				String sourceAsString = response.getSourceAsString();
				result = Json.parse(sourceAsString);
				total = 1;
			}
			return this;
		});
	}

	/**
	 * @param id The resource ID to use for an Elasticsearch GET request
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Index getResource(String id) {
		return withClient((Client client) -> {
			GetResponse response = client.prepareGet(INDEX_NAME, TYPE_RESOURCE, id)
					.execute().actionGet();
			if (response.isExists()) {
				String sourceAsString = response.getSourceAsString();
				result = Json.parse(sourceAsString);
				total = 1;
			}
			return this;
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
	public Aggregations getAggregations() {
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
		int defaultSize = 100;
		Arrays.asList(fields).forEach(field -> {
			if (field.equals("owner")) {
				ChildrenBuilder ownerAggregation =
						AggregationBuilders.children(Application.OWNER_AGGREGATION)
								.childType(TYPE_ITEM).subAggregation(AggregationBuilders
										.terms(field).field("owner.id").size(defaultSize));
				searchRequest.addAggregation(ownerAggregation);
			} else {
				boolean many = field.equals("publication.startDate");
				searchRequest.addAggregation(AggregationBuilders.terms(field)
						.field(field).size(many ? 1000 : defaultSize));
			}
		});
		return searchRequest;
	}

	<T> T withClient(Function<Client, T> function) {
		if (elasticsearchClient != null) {
			return function.apply(elasticsearchClient);
		}
		Settings settings =
				Settings.settingsBuilder().put("cluster.name", CLUSTER_NAME).build();
		try (TransportClient client =
				TransportClient.builder().settings(settings).build()) {
			addHosts(client);
			return function.apply(client);
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
