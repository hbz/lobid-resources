/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import play.libs.Json;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

/**
 * NWBib classification and spatial classification data access via Elasticsearch
 *
 * @author Fabian Steeg (fsteeg)
 */
public class Classification {

	private static final String INDEX = "nwbib";

	private enum Type {
		NWBIB("json-ld-nwbib", "Sachsystematik"), //
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

	Client client;
	private String server;
	private String cluster;

	public Classification(String cluster, String server) {
		this.cluster = cluster;
		this.server = server;
		Settings settings =
				ImmutableSettings.settingsBuilder().put("cluster.name", this.cluster)
						.build();
		InetSocketTransportAddress address =
				new InetSocketTransportAddress(this.server, 9300);
		client = new TransportClient(settings).addTransportAddress(address);
	}

	SearchResponse dataFor(final String tQueryParameter) {
		try {
			for (Type indexType : Type.values())
				if (indexType.queryParameter.equalsIgnoreCase(tQueryParameter))
					return classificationData(indexType.elasticsearchType);
		} catch (Throwable t) {
			return null;
		}
		return null;
	}

	private SearchResponse classificationData(String type) {
		MatchAllQueryBuilder queryBuilder = QueryBuilders.matchAllQuery();
		SearchRequestBuilder requestBuilder =
				client.prepareSearch(INDEX)
						.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
						.setQuery(queryBuilder).setTypes(type).setFrom(0).setSize(1000);
		return requestBuilder.execute().actionGet();
	}

	public JsonNode ids(String query, String t) {
		QueryBuilder queryBuilder =
				QueryBuilders
						.boolQuery()
						.should(QueryBuilders.matchQuery(//
								"@graph." + Property.LABEL.value + ".@value", query))
						.should(
								QueryBuilders.idsQuery(Type.NWBIB.elasticsearchType,
										Type.SPATIAL.elasticsearchType).ids(query))
						.minimumNumberShouldMatch(1);
		SearchRequestBuilder requestBuilder =
				client.prepareSearch(INDEX)
						.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
						.setQuery(queryBuilder);
		if (t.isEmpty()) {
			requestBuilder =
					requestBuilder.setTypes(Type.NWBIB.elasticsearchType,
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

	List<JsonNode> ids(SearchResponse response) {
		List<JsonNode> result = new ArrayList<JsonNode>();
		for (SearchHit hit : response.getHits()) {
			JsonNode json = Json.toJson(hit.getSource());
			collectLabelAndValue(hit, json, Label.PLAIN, result);
		}
		return result;
	}

	JsonNode sorted(SearchResponse response) {
		final List<JsonNode> result =
				ids(response)
						.stream()
						.sorted(
								(JsonNode o1, JsonNode o2) -> Collator.getInstance(
										Locale.GERMANY).compare(labelText(o1), labelText(o2)))
						.collect(Collectors.toList());
		return Json.toJson(result);
	}

	private String labelText(JsonNode node) {
		return node.get("label").asText();
	}

	void buildHierarchy(SearchResponse response, List<JsonNode> topClasses,
			Map<String, List<JsonNode>> subClasses) {
		for (SearchHit hit : response.getHits()) {
			JsonNode json = Json.toJson(hit.getSource());
			JsonNode broader = json.findValue(Property.BROADER.value);
			if (broader == null)
				topClasses.addAll(valueAndLabelWithNotation(hit, json));
			else
				addAsSubClass(subClasses, hit, json, broader.findValue("@id").asText());
		}
	}

	private void addAsSubClass(Map<String, List<JsonNode>> subClasses,
			SearchHit hit, JsonNode json, String broader) {
		if (!subClasses.containsKey(broader))
			subClasses.put(broader, new ArrayList<JsonNode>());
		subClasses.get(broader).addAll(valueAndLabelWithNotation(hit, json));
	}

	private List<JsonNode> valueAndLabelWithNotation(SearchHit hit, JsonNode json) {
		List<JsonNode> result = new ArrayList<JsonNode>();
		collectLabelAndValue(hit, json, Label.WITH_NOTATION, result);
		return result;
	}

	private void collectLabelAndValue(SearchHit hit, JsonNode json, Label style,
			List<JsonNode> result) {
		final JsonNode label = json.findValue(Property.LABEL.value);
		if (label != null) {
			String id = hit.getId();
			ImmutableMap<String, String> map =
					ImmutableMap.of("value", id, "label", (style == Label.PLAIN ? ""
							: shortId(id) + " ") + label.findValue("@value").asText());
			result.add(Json.toJson(map));
		}
	}

	public static String shortId(String id) {
		return id.split("#")[1].substring(1);
	}

}
