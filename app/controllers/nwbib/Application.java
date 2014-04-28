/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.nwbib;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import play.Logger;
import play.cache.Cache;
import play.data.Form;
import play.libs.F.Function;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.WS;
import play.libs.WS.WSRequestHolder;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.browse_classification;
import views.html.browse_register;
import views.html.index;
import views.html.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Application extends Controller {
	private static final File FILE = new File("conf/nwbib.conf");
	public final static Config CONFIG = ConfigFactory.parseFile(
			FILE.exists() ? FILE : new File("modules/nwbib/conf/nwbib.conf"))
			.resolve();
	static Form<String> queryForm = Form.form(String.class);

	private static final String INDEX = "nwbib";
	private static final String NWBIB_TYPE = "json-ld-nwbib";
	private static final String NWBIB_SPATIAL_TYPE = "json-ld-nwbib-spatial";
	private static final String CLUSTER = CONFIG.getString("nwbib.cluster");
	private static final String SERVER = CONFIG.getString("nwbib.server");

	private static enum Label {
		WITH_NOTATION, PLAIN
	}

	final static Client client = new TransportClient(ImmutableSettings
			.settingsBuilder().put("cluster.name", CLUSTER).build())
			.addTransportAddress(new InetSocketTransportAddress(SERVER, 9300));

	public static Result index() {
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(index.render(form));
		} else {
			return ok(index.render(form));
		}
	}

	public static Promise<Result> search(final String q, final int from,
			final int size) {
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors()) {
			return badRequestPromise(q, form, from, size);
		} else {
			final String query = form.data().get("query");
			return okPromise(query != null ? query : q, form, from, size);
		}
	}

	public static Result subject(final String q) {
		JsonNode ids = ids(q);
		final String[] callback = request() == null
				|| request().queryString() == null ? null : request()
				.queryString().get("callback");
		if (callback != null)
			return ok(String.format("%s(%s)", callback[0], Json.stringify(ids)));
		return ok(ids);
	}

	public static Result register(final String t) {
		Result cachedResult = (Result) Cache.get("register." + t);
		if (cachedResult != null)
			return cachedResult;
		SearchResponse response = dataFor(t);
		if (response == null)
			return badRequest("Unsupported register type: " + t);
		JsonNode sorted = sorted(response);
		Result result = ok(browse_register.render(sorted.toString()));
		Cache.set("result." + t, result);
		return result;
	}

	public static Result classification(final String t) {
		Result cachedResult = (Result) Cache.get("classification." + t);
		if (cachedResult != null)
			return cachedResult;
		SearchResponse response = dataFor(t);
		if (response == null)
			return badRequest("Unsupported classification type: " + t);
		Result result = classificationResult(response);
		Cache.set("classification." + t, result);
		return result;
	}

	private static Result classificationResult(SearchResponse response) {
		List<JsonNode> topClasses = new ArrayList<JsonNode>();
		Map<String, List<JsonNode>> subClasses = new HashMap<>();
		for (SearchHit hit : response.getHits()) {
			JsonNode json = Json.toJson(hit.getSource());
			JsonNode broader = json
					.findValue("http://www.w3.org/2004/02/skos/core#broader");
			if (broader == null)
				topClasses.addAll(valueAndLabelWithNotation(hit, json));
			else
				addAsSubClass(subClasses, hit, json,
						shortId(broader.findValue("@id").asText()));
		}
		String topClassesJson = Json.toJson(topClasses).toString();
		return ok(browse_classification.render(topClassesJson, subClasses));
	}

	private static void addAsSubClass(Map<String, List<JsonNode>> subClasses,
			SearchHit hit, JsonNode json, String broader) {
		if (!subClasses.containsKey(broader))
			subClasses.put(broader, new ArrayList<JsonNode>());
		subClasses.get(broader).addAll(valueAndLabelWithNotation(hit, json));
	}

	private static List<JsonNode> valueAndLabelWithNotation(SearchHit hit,
			JsonNode json) {
		List<JsonNode> result = new ArrayList<JsonNode>();
		collectLabelAndValue(hit, json, Label.WITH_NOTATION, result);
		return result;
	}

	private static void collectLabelAndValue(SearchHit hit, JsonNode json,
			Label style, List<JsonNode> result) {
		final JsonNode label = json
				.findValue("http://www.w3.org/2004/02/skos/core#prefLabel");
		if (label != null) {
			String shortId = shortId(hit.getId());
			ImmutableMap<String, String> map = ImmutableMap.of("value",
					shortId, "label",
					(style == Label.PLAIN ? "" : shortId.substring(1) + " ")
							+ label.findValue("@value").asText());
			result.add(Json.toJson(map));
		}
	}

	private static SearchResponse dataFor(final String t) {
		return t.equalsIgnoreCase("Sachsystematik") ? classificationData(NWBIB_TYPE)
				: t.equalsIgnoreCase("Raumsystematik") ? classificationData(NWBIB_SPATIAL_TYPE)
						: null;
	}

	private static Promise<Result> badRequestPromise(final String q,
			final Form<String> form, final int from, final int size) {
		return Promise.promise(new Function0<Result>() {
			public Result apply() {
				return badRequest(search.render(CONFIG, form, null, q, from,
						size));
			}
		});
	}

	private static Promise<Result> okPromise(final String q,
			final Form<String> form, final int from, final int size) {
		final Promise<Result> result = call(q, form, from, size);
		return result.recover(new Function<Throwable, Result>() {
			@Override
			public Result apply(Throwable throwable) throws Throwable {
				throwable.printStackTrace();
				return ok(search.render(CONFIG, form, "[]", q, from, size));
			}
		});
	}

	private static Promise<Result> call(final String q,
			final Form<String> form, final int from, final int size) {
		final WSRequestHolder requestHolder = WS
				.url(CONFIG.getString("nwbib.api"))
				.setHeader("Accept", "application/json")
				.setQueryParameter("set", CONFIG.getString("nwbib.set"))
				.setQueryParameter("format", "full")
				.setQueryParameter("from", from + "")
				.setQueryParameter("size", size + "")
				.setQueryParameter("q", preprocess(q));
		Logger.info("Request URL {}, query params {} ", requestHolder.getUrl(),
				requestHolder.getQueryParameters());
		return requestHolder.get().map(new Function<WS.Response, Result>() {
			public Result apply(WS.Response response) {
				String s = q.isEmpty() ? "[]" : response.asJson().toString();
				return ok(search.render(CONFIG, form, s, q, from, size));
			}
		});
	}

	private static String preprocess(String query) {
		/* Workaround for https://github.com/hbz/nwbib/issues/4 */
		return query.replaceAll("0\\b", "");
	}

	private static SearchResponse classificationData(String t) {
		MatchAllQueryBuilder queryBuilder = QueryBuilders.matchAllQuery();
		SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX)
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				.setQuery(queryBuilder).setTypes(t).setFrom(0).setSize(1000);
		SearchResponse response = requestBuilder.execute().actionGet();
		return response;
	}

	private static JsonNode sorted(SearchResponse response) {
		List<JsonNode> result = ids(response);
		Collections.sort(result, new Comparator<JsonNode>() {
			@Override
			public int compare(JsonNode o1, JsonNode o2) {
				return Collator.getInstance(Locale.GERMANY).compare(
						o1.get("label").asText(), o2.get("label").asText());
			}
		});
		return Json.toJson(result);
	}

	private static JsonNode ids(String query) {
		MatchQueryBuilder queryBuilder = QueryBuilders.matchQuery(
				"@graph.http://www.w3.org/2004/02/skos/core#prefLabel.@value",
				query);
		SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX)
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				.setQuery(queryBuilder)
				.setTypes(NWBIB_TYPE, NWBIB_SPATIAL_TYPE);
		SearchResponse response = requestBuilder.execute().actionGet();
		List<JsonNode> result = ids(response);
		return Json.toJson(result);
	}

	private static List<JsonNode> ids(SearchResponse response) {
		List<JsonNode> result = new ArrayList<JsonNode>();
		for (SearchHit hit : response.getHits()) {
			JsonNode json = Json.toJson(hit.getSource());
			collectLabelAndValue(hit, json, Label.PLAIN, result);
		}
		return result;
	}

	private static String shortId(String id) {
		return id.substring(id.lastIndexOf('#') + 1);
	}
}
