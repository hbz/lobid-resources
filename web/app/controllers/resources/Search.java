package controllers.resources;

import static controllers.resources.Application.AGENT_FIELD;
import static controllers.resources.Application.ISSUED_FIELD;
import static controllers.resources.Application.MEDIUM_FIELD;
import static controllers.resources.Application.OWNER_AGGREGATION;
import static controllers.resources.Application.SUBJECT_FIELD;
import static controllers.resources.Application.TOPIC_AGGREGATION;
import static controllers.resources.Application.TYPE_FIELD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.join.aggregations.ChildrenAggregationBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
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
public class Search {

	static final String INDEX_NAME = Application.CONFIG.getString("index.name");
	private static final String TYPE_ITEM =
			Application.CONFIG.getString("index.type.item");
	static final String TYPE_RESOURCE =
			Application.CONFIG.getString("index.type.resource");

	static final String OWNER_ID_FIELD = "heldBy.id";
	private static final String SPATIAL_LABEL_FIELD = "spatial.label.raw";
	static final String SPATIAL_GEO_FIELD = "spatial.geo";
	private static final String SUBJECT_ID_FIELD = "subject.id";

	private JsonNode result;
	private long total = 0;
	private Aggregations aggregations;

	// optional values:

	private final QueryBuilder query;
	private final int from;
	private final int size;
	private final String sort;
	private final String aggs;

	/**
	 * @param builder The Search.Builder for this search
	 */
	public Search(Builder builder) {
		this.query = builder.query;
		this.from = builder.from;
		this.size = builder.size;
		this.sort = builder.sort;
		this.aggs = builder.aggs;
	}

	/**
	 * Builder pattern for optional arguments.
	 */
	@SuppressWarnings("javadoc")
	public static class Builder {
		private QueryBuilder query = QueryBuilders.queryStringQuery("*");
		private int from = 0;
		private int size = 10;
		private String sort = "";
		private String aggs = "";

		//@formatter:off
		public Builder() {}
		public Builder query(QueryBuilder val) { query = val; return this; }
		public Builder from(int val) { from = val; return this; }
		public Builder size(int val) { size = val; return this; }
		public Builder sort(String val) { sort = val; return this; }
		public Builder aggs(String val) { aggs = val; return this; }
		public Search build() { return new Search(this); }
		//@formatter:on

	}

	/**
	 * The client to use. If null, create default client from settings.
	 */
	public static Client elasticsearchClient = null;

	/**
	 * Fields in the index data that should not be included in the response data.
	 * See https://github.com/hbz/lobid-resources/issues/197
	 */
	public static final List<String> HIDE_FIELDS =
			Arrays.asList("contributorOrder", "subjectOrder", "subjectChain");

	/**
	 * The values supported for the `aggregations` query parameter.
	 */
	public static final List<String> SUPPORTED_AGGREGATIONS =
			Arrays.asList(ISSUED_FIELD, SUBJECT_FIELD, TYPE_FIELD, MEDIUM_FIELD,
					OWNER_AGGREGATION, AGENT_FIELD, SPATIAL_LABEL_FIELD,
					SPATIAL_GEO_FIELD, SUBJECT_ID_FIELD, TOPIC_AGGREGATION);

	/**
	 * @return The number of result for this search
	 */
	public long totalHits() {
		try {
			return Cache.getOrElse("total-" + query,
					() -> queryResources().getTotal(), Application.ONE_DAY);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

	/**
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Search queryResources() {
		Search resultIndex = withClient((Client client) -> {
			validate(client, query);
			Logger.trace("queryResources: q={}, from={}, size={}, sort={}, query={}",
					query, from, size, sort, query);
			SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX_NAME)
					.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
					.setTypes(TYPE_RESOURCE).setQuery(query).setFrom(from).setSize(size);
			if (!sort.isEmpty()) {
				requestBuilder.addSort(SortBuilders.fieldSort("publication.startDate")
						.order(sort.equals("newest") ? SortOrder.DESC : SortOrder.ASC));
			}
			if (!aggs.isEmpty()) {
				requestBuilder = withAggregations(requestBuilder, aggs.split(","));
			}
			SearchResponse response = requestBuilder.execute().actionGet();
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

	static void validate(Client client, QueryBuilder query) {
		ValidateQueryResponse validate =
				client.admin().indices().prepareValidateQuery(INDEX_NAME)
						.setTypes(TYPE_RESOURCE).setQuery(query).get();
		if (!validate.isValid()) {
			throw new IllegalArgumentException("Invalid query: " + query);
		}
	}

	/**
	 * @param id The item ID to use for an Elasticsearch GET request
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Search getItem(String id) {
		return withClient((Client client) -> {
			SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX_NAME)
					.setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setTypes(TYPE_ITEM)
					.setQuery(QueryBuilders.idsQuery().addIds(id)).setSize(1);
			SearchResponse response = requestBuilder.execute().actionGet();
			if (response.getHits().getTotalHits() > 0) {
				String sourceAsString = response.getHits().getAt(0).getSourceAsString();
				result = Json.parse(sourceAsString);
				total = 1;
			} else {
				Logger.warn("No item found for ID {}", id);
			}
			return this;
		});
	}

	/**
	 * @param id The resource ID to use for an Elasticsearch GET request
	 * @return This index, get results via {@link #getResult()} and
	 *         {@link #getTotal()}
	 */
	public Search getResource(String id) {
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
		return result == null ? null : withoutHiddenFields(result);
	}

	private static JsonNode withoutHiddenFields(JsonNode json) {
		return json.isObject() ? filteredObject(json) : filteredArray(json);
	}

	private static JsonNode filteredArray(JsonNode json) {
		List<JsonNode> result = new ArrayList<>();
		json.elements().forEachRemaining(node -> result.add(filteredObject(node)));
		return Json.toJson(result);
	}

	private static JsonNode filteredObject(JsonNode node) {
		Map<String, Object> map = Json.fromJson(node, Map.class);
		HIDE_FIELDS.forEach(fieldToHide -> map.remove(fieldToHide));
		return Json.toJson(map);
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
		Arrays.asList(fields).forEach(field -> {
			int size = field.equals(TOPIC_AGGREGATION) ? 9999
					: (field.equals(ISSUED_FIELD) ? 1000 : 100);
			if (field.equals(OWNER_AGGREGATION)) {
				AggregationBuilder ownerAggregation =
						new ChildrenAggregationBuilder(Application.OWNER_AGGREGATION,
								TYPE_ITEM)
										.subAggregation(AggregationBuilders.terms(field)
												.field(OWNER_ID_FIELD).size(size));
				searchRequest.addAggregation(ownerAggregation);
			} else if (field.equals(SPATIAL_GEO_FIELD)) {
				searchRequest
						.addAggregation(AggregationBuilders.geohashGrid(SPATIAL_GEO_FIELD)
								.field(SPATIAL_GEO_FIELD).precision(9));
			} else {
				searchRequest.addAggregation(
						AggregationBuilders.terms(field).field(field).size(size));
			}
		});
		return searchRequest;
	}

	<T> T withClient(Function<Client, T> function) {
		if (elasticsearchClient != null) {
			return function.apply(elasticsearchClient);
		}
		return null;
	}

}
