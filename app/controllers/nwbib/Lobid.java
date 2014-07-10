package controllers.nwbib;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.HasChildFilterBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsFilterBuilder;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.search.facet.terms.TermsFacetBuilder;

import play.Logger;
import play.cache.Cache;
import play.libs.F.Promise;
import play.libs.WS;
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

	static WSRequestHolder request(final String q, final String author, final String name, final String subject, final int from,
			final int size, String owner, String t) {
		WSRequestHolder requestHolder = WS
				.url(Application.CONFIG.getString("nwbib.api"))
				.setHeader("Accept", "application/json")
				.setQueryParameter("set",
						Application.CONFIG.getString("nwbib.set"))
				.setQueryParameter("format", "full")
				.setQueryParameter("from", from + "")
				.setQueryParameter("size", size + "");
		if(!q.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("q", q);
		if(!author.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("author", author);
		if(!name.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("name", name);
		if(!subject.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("subject", subject);
		if (!owner.equals("all"))
			requestHolder = requestHolder.setQueryParameter("owner", owner);
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
		WSRequestHolder requestHolder = request("", "", "", "", 0, 0, "", "");
		return requestHolder.get().map((WS.Response response) -> {
			Long total = getTotalResults(response.asJson());
			Cache.set("totalHits", total, Application.ONE_HOUR);
			return total;
		});
	}

	public static String organisationLabel(String url){
		final String cachedResult = (String) Cache.get(String.format("org.label."+url));
		if (cachedResult != null) {
				return cachedResult;
		}
		WSRequestHolder requestHolder = WS
				.url(url)
				.setHeader("Accept", "application/json")
				.setQueryParameter("format","short.name");
		return requestHolder.get().map((WS.Response response) -> {
			String label = response.asJson().elements().next().asText();
			Cache.set("org.label."+url, label, Application.ONE_HOUR);
			return label;
		}).get(10000);
	}

	public static Promise<Facets> getFacets(String q, String owner, String field) {
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
			if (!owner.equals("all")) {
				String fieldName = "@graph.http://purl.org/vocab/frbr/core#exemplar.@id";
				FilterBuilder filter = owner.equals("*") ? FilterBuilders.existsFilter(fieldName)
						: ownersFilter(owner, fieldName);
				facet = facet.facetFilter(filter);
			}
			req = req.addFacet(facet);
			long start = System.currentTimeMillis();
			SearchResponse res = req.execute().actionGet();
			Facets facets = res.getFacets();
			Logger.debug("Facets for q={}, owner={}, facets={}: {}; took {} ms.", 
					q, owner, field, facets, (System.currentTimeMillis() - start));
			return facets;
		}).recover((Throwable x) -> {
			x.printStackTrace();
			return null;
		});
	}

	private static FilterBuilder ownersFilter(final String ownerParam,
			String fieldName) {
		TermsFilterBuilder filter = FilterBuilders.termsFilter(
				"@graph.http://purl.org/vocab/frbr/core#owner.@id",
				ownerParam.split(","));
		HasChildFilterBuilder result = FilterBuilders.hasChildFilter(
				"json-ld-lobid-item", filter);
		return result;
	}

	public static String typeLabel(List<String> types) {
		String type = selectType(types);
		if(type.isEmpty()) return "";
		@SuppressWarnings("unchecked")
		String selected = ((List<String>) Application.CONFIG
				.getObject("type.labels").unwrapped().get(type)).get(0);
		return selected.isEmpty() ? types.get(0) : selected;
	}

	public static String typeIcon(List<String> types) {
		String type = selectType(types);
		if(type.isEmpty()) return "";
		@SuppressWarnings("unchecked")
		String selected = ((List<String>) Application.CONFIG
				.getObject("type.labels").unwrapped().get(type)).get(1);
		return selected.isEmpty() ? types.get(0) : selected;
	}

	private static String selectType(List<String> types) {
		Logger.trace("Types: " + types);
		@SuppressWarnings("unchecked")
		List<String> selected = types.stream()
			.map(t -> {
				List<String> vals = ((List<String>) Application.CONFIG
				.getObject("type.labels").unwrapped().get(t));
				return vals == null || vals.get(0).isEmpty()
						|| vals.get(1).isEmpty() ? "" : t;
			})
			.filter(t -> { return !t.isEmpty(); })
			.collect(Collectors.toList());
		Collections.sort(selected);
		Logger.trace("Selected: " + selected);
		return selected.isEmpty() ? "" : selected.get(0);
	}
}
