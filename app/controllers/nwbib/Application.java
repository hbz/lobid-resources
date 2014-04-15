/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.nwbib;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.nwbib_index;

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
		return ok(ids(q));
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
			ImmutableMap<String, String> map = ImmutableMap.of(
					"value",
					hit.getId(),
					"label",
					json.findValue(
							"http://www.w3.org/2004/02/skos/core#prefLabel")
							.findValue("@value").asText());
			result.add(Json.toJson(map));
		}
		return result;
	}

	public static String url(String query) {
		final String template = "%s/resource?set=%s&format=full&from=0&size=50&q=%s";
		try {
			return String.format(template, CONFIG.getString("nwbib.api"),
					CONFIG.getString("nwbib.set"),
					URLEncoder.encode(query, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return "";
	}

	public static String call(final String url) {
		try {
			final URLConnection connection = new URL(url).openConnection();
			return CharStreams.toString(new InputStreamReader(connection
					.getInputStream(), Charsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
