package controllers.nwbib;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.search.facet.terms.TermsFacetBuilder;

import play.Logger;
import play.cache.Cache;
import play.libs.WS;
import play.libs.F.Promise;
import play.libs.WS.WSRequestHolder;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Access Lobid title data.
 * 
 * @author fsteeg
 *
 */
public class Lobid {

	static Long getTotalResults(JsonNode json) {
		return json.findValue("http://sindice.com/vocab/search#totalResults")
				.asLong();
	}

	static WSRequestHolder request(final String q, final int from,
			final int size, boolean all, String t) {
		WSRequestHolder requestHolder = WS
				.url(Application.CONFIG.getString("nwbib.api"))
				.setHeader("Accept", "application/json")
				.setQueryParameter("set",
						Application.CONFIG.getString("nwbib.set"))
				.setQueryParameter("format", "full")
				.setQueryParameter("from", from + "")
				.setQueryParameter("size", size + "")
				.setQueryParameter("q", q);
		if (!all)
			requestHolder = requestHolder.setQueryParameter("owner", "*");
		if(!t.isEmpty())
			requestHolder = requestHolder.setQueryParameter("t", t);
		Logger.info("Request URL {}, query params {} ", requestHolder.getUrl(),
				requestHolder.getQueryParameters());
		return requestHolder;
	}

	public static Promise<Long> getTotalHits() {
		final Long cachedResult = (Long) Cache.get(String.format("totalHits"));
		if (cachedResult != null) {
			return Promise.promise(() -> {
				return cachedResult;
			});
		}
		WSRequestHolder requestHolder = request("", 0, 0, true, "");
		return requestHolder.get().map((WS.Response response) -> {
			Long total = getTotalResults(response.asJson());
			Cache.set("totalHits", total, Application.ONE_HOUR);
			return total;
		});
	}

	public static Promise<Facets> getFacets(String q, boolean all, String field) {
		return Promise.promise(() -> {
			BoolQueryBuilder query = QueryBuilders
					.boolQuery()
					.must(q.isEmpty() ? QueryBuilders.matchAllQuery()
							: QueryBuilders.queryString(q).field("_all"))
					.must(QueryBuilders.matchQuery(
							"@graph.http://purl.org/dc/terms/isPartOf.@id",
							Application.CONFIG.getString("nwbib.set")).operator(
							MatchQueryBuilder.Operator.AND));
			SearchRequestBuilder req = Application.CLASSIFICATION.client
					.prepareSearch("lobid-resources")
					.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
					.setQuery(query).setTypes("json-ld-lobid").setFrom(0)
					.setSize(0);
			TermsFacetBuilder facet = FacetBuilders.termsFacet(field).field(field);
			if (!all){
				facet = facet.facetFilter(FilterBuilders.existsFilter(//
						"@graph.http://purl.org/vocab/frbr/core#exemplar.@id"));
			}
			req = req.addFacet(facet);
			SearchResponse res = req.execute().actionGet();
			Facets facets = res.getFacets();
			Logger.debug("Facets for q={}, all={}, facets={}: {}", q, all, field, facets);
			return facets;
		}).recover((Throwable t) -> {
			t.printStackTrace();
			return null;
		});
	}
}
