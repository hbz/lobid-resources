/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import static controllers.nwbib.Application.CONFIG;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHit;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaRDFParser;
import com.github.jsonldjava.utils.JSONUtils;
import com.google.common.collect.ImmutableMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import play.Logger;
import play.libs.Json;

/**
 * NWBib classification and spatial classification data access via Elasticsearch
 *
 * @author Fabian Steeg (fsteeg)
 */
public class Classification {

	private static final String INDEX = "nwbib";

	/**
	 * NWBib classification types.
	 */
	public enum Type {
		/** NWBib subject type */
		NWBIB("json-ld-nwbib", "Sachsystematik"), //
		/** NWBib spatial type */
		SPATIAL("json-ld-nwbib-spatial", "Raumsystematik");

		String elasticsearchType;
		String queryParameter;

		private Type(String elasticsearchType, String queryParameter) {
			this.elasticsearchType = elasticsearchType;
			this.queryParameter = queryParameter;
		}
	}

	private enum Property {
		LABEL("http://www.w3.org/2004/02/skos/core#prefLabel"), //
		BROADER("http://www.w3.org/2004/02/skos/core#broader");

		String value;

		private Property(String value) {
			this.value = value;
		}
	}

	private enum Label {
		WITH_NOTATION, PLAIN
	}

	private static Client client;
	private static Node node;

	static Comparator<JsonNode> comparator =
			(JsonNode o1, JsonNode o2) -> Collator.getInstance(Locale.GERMAN)
					.compare(labelText(o1), labelText(o2));

	private Classification() {
		/* Use via static functions, no instantiation. */
	}

	/**
	 * @param turtleUrl The URL of the RDF in TURTLE format
	 * @return The input, converted to JSON-LD, or null
	 */
	public static List<String> toJsonLd(final URL turtleUrl) {
		final Model model = ModelFactory.createDefaultModel();
		try {
			model.read(turtleUrl.openStream(), null, "TURTLE");
			final JenaRDFParser parser = new JenaRDFParser();
			Object json = JsonLdProcessor.fromRDF(model, new JsonLdOptions(), parser);
			List<Object> list = JsonLdProcessor.expand(json);
			return list.subList(1, list.size()).stream().map(JSONUtils::toString)
					.collect(Collectors.toList());
		} catch (JsonLdError | IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	static SearchResponse dataFor(final String tQueryParameter) {
		try {
			for (Type indexType : Type.values())
				if (indexType.queryParameter.equalsIgnoreCase(tQueryParameter))
					return classificationData(indexType.elasticsearchType);
		} catch (Throwable t) {
			return null;
		}
		return null;
	}

	private static SearchResponse classificationData(String type) {
		MatchAllQueryBuilder queryBuilder = QueryBuilders.matchAllQuery();
		SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX)
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setQuery(queryBuilder)
				.setTypes(type).setFrom(0).setSize(1000);
		return requestBuilder.execute().actionGet();
	}

	/**
	 * @param q The query
	 * @param t The classification type ("Raumsystematik" or "Sachsystematik")
	 * @return A JSON representation of the classification data for q and t
	 */
	public static JsonNode ids(String q, String t) {
		QueryBuilder queryBuilder = QueryBuilders.boolQuery()
				.should(QueryBuilders.matchQuery(//
						"@graph." + Property.LABEL.value + ".@value", q))
				.should(QueryBuilders.idsQuery(Type.NWBIB.elasticsearchType,
						Type.SPATIAL.elasticsearchType).ids(q))
				.minimumNumberShouldMatch(1);
		SearchRequestBuilder requestBuilder = client.prepareSearch(INDEX)
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setQuery(queryBuilder);
		if (t.isEmpty()) {
			requestBuilder = requestBuilder.setTypes(Type.NWBIB.elasticsearchType,
					Type.SPATIAL.elasticsearchType);
		} else {
			for (Type indexType : Type.values())
				if (indexType.queryParameter.equalsIgnoreCase(t))
					requestBuilder = requestBuilder.setTypes(indexType.elasticsearchType);
		}
		SearchResponse response = requestBuilder.execute().actionGet();
		List<JsonNode> result = ids(response);
		return Json.toJson(result);
	}

	/**
	 * @param uri The NWBib classificationURI
	 * @param type The ES classification type (see {@link Classification.Type})
	 * @return The label for the given URI
	 */
	public static String label(String uri, String type) {
		try {
			String response =
					client.prepareGet(INDEX, type, uri).get().getSourceAsString();
			if (response != null) {
				String textValue = Json.parse(response)
						.findValue("http://www.w3.org/2004/02/skos/core#prefLabel")
						.findValue("@value").textValue();
				return textValue != null ? textValue : "";
			}
		} catch (Throwable t) {
			Logger.error(
					"Could not get classification data, index: {} type: {}, id: {} ({}: {})",
					INDEX, type, uri, t, t.getMessage());
			t.printStackTrace();
		}
		return "";
	}

	static List<JsonNode> ids(SearchResponse response) {
		List<JsonNode> result = new ArrayList<>();
		for (SearchHit hit : response.getHits()) {
			JsonNode json = Json.toJson(hit.getSource());
			collectLabelAndValue(hit, json, Label.PLAIN, result);
		}
		return result;
	}

	static JsonNode sorted(SearchResponse response) {
		final List<JsonNode> result =
				ids(response).stream().sorted(comparator).collect(Collectors.toList());
		return Json.toJson(result);
	}

	private static String labelText(JsonNode json) {
		return json.get("label").asText();
	}

	static void buildHierarchy(SearchResponse response, List<JsonNode> topClasses,
			Map<String, List<JsonNode>> subClasses) {
		for (SearchHit hit : response.getHits()) {
			JsonNode json = Json.toJson(hit.getSource());
			JsonNode broader = json.findValue(Property.BROADER.value);
			if (broader == null)
				topClasses.addAll(valueAndLabelWithNotation(hit, json));
			else
				addAsSubClass(subClasses, hit, json, broader.findValue("@id").asText());
		}
		Collections.sort(topClasses, comparator);
	}

	private static void addAsSubClass(Map<String, List<JsonNode>> subClasses,
			SearchHit hit, JsonNode json, String broader) {
		if (!subClasses.containsKey(broader))
			subClasses.put(broader, new ArrayList<JsonNode>());
		List<JsonNode> list = subClasses.get(broader);
		list.addAll(valueAndLabelWithNotation(hit, json));
		Collections.sort(list, comparator);
	}

	private static List<JsonNode> valueAndLabelWithNotation(SearchHit hit,
			JsonNode json) {
		List<JsonNode> result = new ArrayList<>();
		collectLabelAndValue(hit, json, Label.WITH_NOTATION, result);
		return result;
	}

	private static void collectLabelAndValue(SearchHit hit, JsonNode json,
			Label style, List<JsonNode> result) {
		final JsonNode label = json.findValue(Property.LABEL.value);
		if (label != null) {
			String id = hit.getId();
			ImmutableMap<String, String> map = ImmutableMap.of("value", id, "label",
					(style == Label.PLAIN ? "" : shortId(id) + " ")
							+ label.findValue("@value").asText());
			result.add(Json.toJson(map));
		}
	}

	/**
	 * @param uri The full URI
	 * @return A short, human readable representation of the URI
	 */
	public static String shortId(String uri) {
		return uri.split("#")[1].substring(1);
	}

	/** Start up the embedded Elasticsearch classification index. */
	public static void indexStartup() {
		Settings clientSettings = ImmutableSettings.settingsBuilder()
				.put("path.home", new File(".").getAbsolutePath())
				.put("http.port", CONFIG.getString("index.es.port.http"))
				.put("transport.tcp.port", CONFIG.getString("index.es.port.tcp"))
				.build();
		node =
				NodeBuilder.nodeBuilder().settings(clientSettings).local(true).node();
		client = node.client();
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute()
				.actionGet();
		if (!client.admin().indices().prepareExists(INDEX).execute().actionGet()
				.isExists()) {
			indexData(CONFIG.getString("index.data.nwbibsubject"), Type.NWBIB);
			indexData(CONFIG.getString("index.data.nwbibspatial"), Type.SPATIAL);
		}
	}

	private static void indexData(String dataUrl, Type type) {
		Logger.debug("Indexing from dataUrl: {}, type: {}, index: {}, client {}",
				dataUrl, type.elasticsearchType, INDEX, client);
		final BulkRequestBuilder bulkRequest = client.prepareBulk();
		try {
			List<String> jsonLd = toJsonLd(new URL(dataUrl));
			for (String concept : jsonLd) {
				String id = Json.parse(concept).findValue("@id").textValue();
				IndexRequestBuilder indexRequest = client
						.prepareIndex(INDEX, type.elasticsearchType, id).setSource(concept);
				bulkRequest.add(indexRequest);
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		BulkResponse response = bulkRequest.execute().actionGet();
		if (response.hasFailures()) {
			Logger.info("Indexing response: {}", response.buildFailureMessage());
		}
	}

	/** Shut down the embedded Elasticsearch classification index. */
	public static void indexShutdown() {
		node.close();
	}

}
