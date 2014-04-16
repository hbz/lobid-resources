/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.nwbib;

import java.io.File;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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

import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.nwbib_index;
import views.html.nwbib_register;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
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

	final static Client client = new TransportClient(ImmutableSettings
			.settingsBuilder().put("cluster.name", CLUSTER).build())
			.addTransportAddress(new InetSocketTransportAddress(SERVER, 9300));

	public static Result index(final String q) {
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(nwbib_index.render(CONFIG, form, null, null, q));
		} else {
			final String query = form.data().get("query");
			final String url = url(query != null ? query : q);
			final String result = call(url);
			return ok(nwbib_index.render(CONFIG, form, url, result, q));
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
		String type = t.equalsIgnoreCase("Sachsystematik") ? NWBIB_TYPE : t
				.equalsIgnoreCase("Raumsystematik") ? NWBIB_SPATIAL_TYPE : null;
		if (type == null)
			return badRequest("Unsupported register type: " + t);
		return ok(nwbib_register.render(classification(type).toString()));
	}

	private static JsonNode classification(String t) {
		MatchAllQueryBuilder queryBuilder = QueryBuilders.matchAllQuery();
		SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX)
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				.setQuery(queryBuilder).setTypes(t).setFrom(0).setSize(1000);
		SearchResponse response = requestBuilder.execute().actionGet();
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
			JsonNode label = json
					.findValue("http://www.w3.org/2004/02/skos/core#prefLabel");
			if (label != null) {
				ImmutableMap<String, String> map = ImmutableMap.of("value",
						"\"" + hit.getId() + "\"", "label",
						label.findValue("@value").asText());
				result.add(Json.toJson(map));
			}
		}
		return result;
	}

	public static String url(String query) {
		final String template = "%s/resource?set=%s&format=full&from=0&size=50&q=%s";
		try {
			return String.format(template, CONFIG.getString("nwbib.api"),
					CONFIG.getString("nwbib.set"),
					URLEncoder.encode(preprocess(query), "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return "";
	}

	private static String preprocess(String query) {
		/* Workaround for https://github.com/hbz/nwbib/issues/4 */
		return query.replaceAll("0\\b", "");
	}

	public static String call(final String url) {
		try {
			final URLConnection connection = new URL(url).openConnection();
			final String result = CharStreams.toString(new InputStreamReader(
					connection.getInputStream(), Charsets.UTF_8));
			return Json.parse(result).toString();
		} catch (Exception e) {
			e.printStackTrace();
			return "[]";
		}
	}
}
