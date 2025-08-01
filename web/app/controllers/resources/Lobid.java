/* Copyright 2014-2019 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.HtmlEscapers;

import play.Logger;
import play.api.libs.json.JsValue;
import play.cache.Cache;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.mvc.Http;
import play.mvc.Http.Status;
import play.mvc.Result;
import play.test.Helpers;

/**
 * Access Lobid API for labels and related things.
 *
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Lobid {

	/** The lobid-organisations root URL. */
	static final String ORGS_ROOT = Application.CONFIG.getString("orgs.api");
	/** Timeout for API calls in milliseconds. */
	public static final int API_TIMEOUT = 50000;

	/**
	 * @param url The URL to call
	 * @return A JSON response from the URL, or an empty JSON object
	 */
	public static JsonNode cachedJsonCall(String url) {
		String cacheKey = String.format("json.%s", url);
		JsonNode json = (JsonNode) Cache.get(cacheKey);
		if (json != null) {
			return json;
		}
		Logger.trace("Not cached, GET: {}", url);
		Promise<JsonNode> promise =
				WS.url(url).get().map(response -> response.getStatus() == Http.Status.OK
						? response.asJson() : Json.newObject());
		promise.onRedeem(jsonResponse -> {
			Cache.set(cacheKey, jsonResponse, Application.ONE_DAY);
		});
		return promise.get(Lobid.API_TIMEOUT);
	}

	static Long getTotalResults(JsonNode json) {
		return json.findValue("http://sindice.com/vocab/search#totalResults")
				.asLong();
	}

	/**
	 * @param string The URI string to escape
	 * @return The URI string, escaped to be usable as an ES field or value
	 */
	public static String escapeUri(String string) {
		return string.replaceAll("([\\.:/])", "\\\\$1");
	}

	/**
	 * @param id A Lobid-Organisations URI or ISIL
	 * @return A human readable label for the given id
	 */
	public static String organisationLabel(String id) {
		// e.g. take DE-6 from http://lobid.org/organisations/DE-6#!
		String simpleId =
				id.replaceAll("https?://lobid.org/organisations?/(.+?)(#!)?$", "$1");
		JsonNode org =
				cachedJsonCall(id.startsWith("http") ? id : ORGS_ROOT + id);
		if (org.size() == 0) {
			if (simpleId.split("-").length == 3) {
				String superSigel = id.substring(0, id.lastIndexOf('-'));
				Logger.info("No data for: {}, trying {}", id, superSigel);
				return organisationLabel(superSigel) + ": " + simpleId;
			}
			Logger.warn("No data for: " + id);
			return simpleId;
		}
		JsonNode json = Optional.ofNullable(org.findValue("alternateName"))
				.orElse(Json.toJson(Arrays.asList(org.findValue("name"))));
		String label = HtmlEscapers.htmlEscaper()
				.escape(json == null ? "" : json.elements().next().asText());
		Logger.trace("Get org label, {} -> {} -> {}", id, simpleId, label);
		return label.isEmpty() ? simpleId : label;
	}

	/**
	 * @param id A Lobid-Resources URI or hbz title ID
	 * @return A human readable label for the given id
	 */
	public static String resourceLabel(String id) {
		Callable<String> getLabel = () -> {
			// e.g. take TT000086525 from http://lobid.org/resources/TT000086525#!
			String simpleId =
					id.replaceAll("https?://lobid.org/resources?/(.+?)(#!)?$", "$1");
			Result result = Application.resource(simpleId, "json").get(API_TIMEOUT);
			if (result.status() != Status.OK) {
				// In an almaMmsId-based setup, we might still link to others via hbzId
				// (see https://github.com/hbz/lobid-resources/issues/1592 for details)
				String actualId = Application.idSearchResult(simpleId);
				result = Application.resource(actualId, "json").get(API_TIMEOUT);
			}
			JsonNode json =
					Json.parse(Helpers.contentAsString(result)).findValue("title");
			String label =
					json == null ? "" : HtmlEscapers.htmlEscaper().escape(json.asText());
			Logger.debug("Get res label, {} -> {} -> {}", id, simpleId, label);
			return label.isEmpty() ? simpleId : label;
		};
		try {
			return Cache.getOrElse("res.label." + id, getLabel, Application.ONE_DAY);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	private static String gndLabel(String uri, String field) {
		Callable<String> getLabel = () -> {
			return new Search.Builder().build().withClient((Client client) -> {
				QueryBuilder query = QueryBuilders
						.queryStringQuery("* AND " + field + ":\"" + uri + "\"");
				SearchRequestBuilder requestBuilder = client
						.prepareSearch(Search.INDEX_NAME).setTypes(Search.TYPE_RESOURCE)
						.setQuery(query).setFrom(0).setSize(1).setExplain(false);
				SearchResponse response = requestBuilder.execute().actionGet();
				if (response.getHits().getTotalHits() == 0) {
					return uri;
				}
				return field.equals(Application.SUBJECT_FIELD)
						? findSubjectLabelForUriInResponse(uri, response)
						: findContributionLabelForUriInResponse(uri, response);
			});
		};
		try {
			return Cache.getOrElse("gnd.label." + uri, getLabel, Application.ONE_DAY);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return uri;
	}

	private static String findSubjectLabelForUriInResponse(String uri,
			SearchResponse response) {
		List<JsonNode> complexSubjects =
				Json.toJson(response.getHits().getAt(0).getSource())
						.findValues("componentList");
		String label =
				complexSubjects.stream()
						.flatMap((complexSubject) -> StreamSupport.stream(Spliterators
								.spliteratorUnknownSize(complexSubject.elements(), 0), false))
						.filter((s) -> s.has("id") && s.get("id").textValue().equals(uri))
						.findFirst().map((s) -> s.get("label").textValue()).orElse(uri);
		return label;
	}

	private static String findContributionLabelForUriInResponse(String uri,
			SearchResponse response) {
		Object contributions =
				response.getHits().getAt(0).getSource().get("contribution");
		@SuppressWarnings("unchecked") // always array of JSON objects
		String label =
				((List<HashMap<String, Object>>) contributions).stream().filter((c) -> {
					Map<String, Object> agent = (Map<String, Object>) c.get("agent");
					return agent.containsKey("id") && agent.get("id").equals(uri);
				}).findFirst().map((s) -> ((Map<String, Object>) s.get("agent"))
						.get("label").toString()).orElse(uri);
		return label;
	}

	private static final Map<String, String> keys =
			ImmutableMap.of(Application.TYPE_FIELD, "type.labels", //
					Application.MEDIUM_FIELD, "medium.labels");

	/**
	 * @param types Some type URIs
	 * @return An icon CSS class for the URIs
	 */
	public static String typeIcon(List<String> types) {
		return facetIcon(types, Application.TYPE_FIELD);
	}

	/**
	 * @param types Some type URIs
	 * @return A human readable label for the URIs
	 */
	public static String typeLabel(List<String> types) {
		return facetLabel(types, Application.TYPE_FIELD, "Publikationstypen");
	}

	/**
	 * @param queryValues The value string of the query, e.g. <br/>
	 *          `"Eisenbahnlinie,https://d-nb.info/gnd/4129465-8"`
	 * @return The given string, without URIs, e.g.`"Eisenbahnlinie"`
	 */
	public static String withoutUris(String queryValues) {
		return Arrays.asList(queryValues.split(",")).stream()
				.filter(s -> !s.startsWith("http://") && !s.matches("AND|OR"))
				.collect(Collectors.joining(","));
	}

	/**
	 * @param uris Some URIs
	 * @param field The ES field to facet over
	 * @param label A label for the facet
	 * @return A human readable label for the URIs
	 */
	public static String facetLabel(List<String> uris, String field,
			String label) {
		if (uris.size() == 1 && uris.get(0).contains(",") && !label.isEmpty()) {
			int length = Arrays.asList(uris.get(0).split(",")).stream()
					.filter(s -> !s.trim().isEmpty()).toArray().length;
			return String.format("%s: %s ausgewählt", label, length);
		}
		if (uris.size() == 1 && isOrg(uris.get(0)))
			return Lobid.organisationLabel(uris.get(0));
		else if (uris.size() == 1 && isGnd(uris.get(0))) {
			return Lobid.gndLabel(uris.get(0), field);
		}
		String configKey = keys.getOrDefault(field, "");
		String type = selectType(uris, configKey);
		if (type.isEmpty())
			return "";
		@SuppressWarnings("unchecked")
		List<String> details = configKey.isEmpty() ? uris
				: ((List<String>) Application.CONFIG.getObject(configKey).unwrapped()
						.get(type));
		if (details == null || details.size() < 1)
			return type;
		String selected = details.get(0).replace("<", "&lt;").replace(">", "&gt;");
		return selected.isEmpty() ? uris.get(0) : selected;
	}

	/**
	 * @param uris Some URIs
	 * @param field The ES field to facet over
	 * @return An icon CSS class for the given URIs
	 */
	public static String facetIcon(List<String> uris, String field) {
		if ((uris.size() == 1)
				&& field.equals(Application.COLLECTION_AGGREGATION))
			return "glyphicon glyphicon-folder-close";
		else if ((uris.size() == 1 && isOrg(uris.get(0)))
				|| field.equals(Application.OWNER_AGGREGATION))
			return "octicon octicon-home";
		else if ((uris.size() == 1 && isGnd(uris.get(0)))
				|| field.equals(Application.SUBJECT_FIELD))
			return "octicon octicon-tag";
		else if (field.equals(Application.ISSUED_FIELD))
			return "glyphicon glyphicon-asterisk";
		String configKey = keys.getOrDefault(field, "");
		String type = selectType(uris, configKey);
		if (type.isEmpty())
			return "";
		@SuppressWarnings("unchecked")
		List<String> details = configKey.isEmpty() ? uris
				: (List<String>) Application.CONFIG.getObject(configKey).unwrapped()
						.get(type);
		if (details == null || details.size() < 2)
			return type;
		String selected = details.get(1);
		return selected.isEmpty() ? uris.get(0) : selected;
	}

	/**
	 * @param types The type uris associated with a resource
	 * @param configKey The key from the config file (icons or labels)
	 * @return The most specific of the passed types
	 */
	public static String selectType(List<String> types, String configKey) {
		if (configKey.isEmpty())
			return types.get(0);
		Logger.trace("Types: " + types);
		@SuppressWarnings("unchecked")
		List<Pair<String, Integer>> selected = types.stream().map(t -> {
			List<Object> vals = ((List<Object>) Application.CONFIG
					.getObject(configKey).unwrapped().get(t));
			if (vals == null)
				return Pair.of(t, 0);
			Integer specificity = (Integer) vals.get(2);
			return ((String) vals.get(0)).isEmpty()
					|| ((String) vals.get(1)).isEmpty() //
							? Pair.of("", specificity) : Pair.of(t, specificity);
		}).filter(t -> {
			return !t.getLeft().isEmpty();
		}).collect(Collectors.toList());
		Collections.sort(selected, (a, b) -> b.getRight().compareTo(a.getRight()));
		Logger.trace("Selected: " + selected);
		return selected.isEmpty() ? ""
				: selected.get(0).getLeft().contains("Miscellaneous")
						&& selected.size() > 1 ? selected.get(1).getLeft()
								: selected.get(0).getLeft();
	}

	static boolean isOrg(String term) {
		return term.contains("lobid.org/organisation");
	}

	/**
	 * @param doc The result JSON doc
	 * @return A mapping of item IDs (URIs) to item details (JSON strings)
	 */
	public static Map<String, String> itemDetails(String doc) {
		JsonNode items = Json.parse(doc).findValue("hasItem");
		Map<String, String> result = new HashMap<>();
		if (items != null && (items.isArray() || items.isTextual())) {
			Iterator<JsonNode> elements =
					items.isArray() ? items.elements() : Arrays.asList(items).iterator();
			while (elements.hasNext()) {
				JsonNode nextItem = elements.next();
				result.put(nextItem.get("id").asText(), nextItem.toString());
			}
		}
		return result;
	}

	private static boolean isGnd(String term) {
		return term.startsWith("https://d-nb.info/gnd");
	}

	/**
	 * @param doc The result JSON doc
	 * @return A mapping of ISILs to item URIs
	 */
	public static Map<String, List<String>> items(String doc) {
		JsonNode items = Json.parse(doc).findValue("hasItem");
		Map<String, List<String>> result = new HashMap<>();
		if (items != null && (items.isArray() || items.isTextual()))
			mapIsilsToUris(items, result);
		return result;
	}

	private static void mapIsilsToUris(JsonNode items,
			Map<String, List<String>> result) {
		Iterator<JsonNode> elements =
				items.isArray() ? items.elements() : Arrays.asList(items).iterator();
		while (elements.hasNext()) {
			String itemUri = elements.next().get("id").asText();
			try {
				String isil = itemUri.split(":")[2];
				List<String> uris = result.getOrDefault(isil, new ArrayList<>());
				uris.add(itemUri);
				result.put(isil, uris);
			} catch (ArrayIndexOutOfBoundsException x) {
				Logger.error(
						"Can't process item URI {}, expecting ISIL at index {} when splitting at ':'",
						itemUri, x.getMessage());
				Logger.debug(x.getMessage(), x);
			}
		}
	}

	/**
	 * Compare ISILs for sorting.
	 *
	 * @param i1 The first ISIL
	 * @param i2 The second ISIL
	 * @return True, if i1 should come before i2
	 */
	public static boolean compareIsil(String i1, String i2) {
		String[] all1 = i1.split("-");
		String[] all2 = i2.split("-");
		if (all1.length == 3 && all2.length == 3) {
			if (all1[1].equals(all2[1])) {
				// use secondary if main is equal, e.g. DE-5-11 before DE-5-20
				return numerical(all1[2]) < numerical(all2[2]);
			}
		} else if (all1.length == 2 && all2.length == 2) {
			if (all1[1].equals(all2[1])) {
				// same main sigel, prefer shorter, e.g. DE-5 before DE-5-11
				return all1.length < all2.length;
			}
			// compare by main sigel, e.g. DE-5 before DE-6:
			return numerical(all1[1]) < numerical(all2[1]);
		}
		// not actually an ISIL, compare given values as numbers:
		return numerical(i1) < numerical(i2);
	}

	private static long numerical(String s) {
		// replace non-digits with 9, e.g. for DE-5 before DE-Walb1
		return Long.parseLong(s.replaceAll("\\D", "9"));
	}

}
