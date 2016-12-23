/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.resources;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.HtmlEscapers;

import play.Logger;
import play.Play;
import play.cache.Cache;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import play.mvc.Http;
import views.TableRow;

/**
 * Access Lobid title data.
 *
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Lobid {

	/** Timeout for API calls in milliseconds. */
	public static final int API_TIMEOUT = 50000;

	/**
	 * @param id The resource ID
	 * @return The resource JSON content
	 */
	public static JsonNode getResource(String id) {
		String url = String
				.format(Application.CONFIG.getString("lobid2.indexUrlFormat"), id);
		return cachedJsonCall(url);
	}

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
		Logger.debug("Not cached, GET: {}", url);
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

	static WSRequestHolder request(final String q, final String person,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final int from, final int size, String owner, String t, String sort,
			String set, String location, String word, String corporation,
			String raw) {
		WSRequestHolder requestHolder = WS
				.url(Application.CONFIG.getString("resources.api"))
				.setHeader("Accept", "application/json")
				.setQueryParameter("format", "full")
				.setQueryParameter("from", from + "")
				.setQueryParameter("size", size + "").setQueryParameter("sort", sort)
				.setQueryParameter("location", locationPolygon(location));
		if (!set.isEmpty() && !set.equals("*"))
			requestHolder = requestHolder.setQueryParameter("set", set);
		else {
			requestHolder = requestHolder.setQueryParameter("set", "");
		}
		if (!raw.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("q", raw);
		if (!q.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("word", preprocess(q));
		else if (!word.isEmpty())
			requestHolder = requestHolder.setQueryParameter("word", preprocess(word));
		if (!person.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("author", person);
		if (!name.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("name", name);
		if (!subject.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("subject", subject);
		if (!id.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("id", id);
		if (!publisher.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("publisher", publisher);
		if (!issued.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("issued", issued);
		if (!medium.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("medium", medium);
		if (!owner.isEmpty())
			requestHolder = requestHolder.setQueryParameter("owner", owner);
		if (!t.isEmpty())
			requestHolder = requestHolder.setQueryParameter("t", t);
		if (!corporation.isEmpty())
			requestHolder =
					requestHolder.setQueryParameter("corporation", corporation);
		Logger.info("Request URL {}, query params {} ", requestHolder.getUrl(),
				requestHolder.getQueryParameters());
		return requestHolder;
	}

	static WSRequestHolder topicRequest(final String q, int from, int size) {
		WSRequestHolder requestHolder = // @formatter:off
				WS.url(Application.CONFIG.getString("resources.api"))
						.setHeader("Accept", "application/json")
						.setQueryParameter("format", "short.subjectChain")
						.setQueryParameter("from", "" + from)
						.setQueryParameter("size", "" + size)
						.setQueryParameter("subject", q);
		//@formatter:on
		Logger.info("Request URL {}, query params {} ", requestHolder.getUrl(),
				requestHolder.getQueryParameters());
		return requestHolder;
	}

	/**
	 * @param set The data set, uses the config file set if empty
	 * @return The full number of hits, ie. the size of the given data set
	 */
	public static Promise<Long> getTotalHits(String set) {
		String cacheKey = String.format("totalHits.%s", set);
		final Long cachedResult = (Long) Cache.get(cacheKey);
		if (cachedResult != null) {
			return Promise.promise(() -> {
				return cachedResult;
			});
		}
		WSRequestHolder requestHolder = request("", "", "", "", "", "", "", "", 0,
				0, "", "", "", set, "", "", "", "");
		return requestHolder.get().map((WSResponse response) -> {
			Long total = getTotalResults(response.asJson());
			Cache.set(cacheKey, total, Application.ONE_HOUR);
			return total;
		});
	}

	/**
	 * @param field The Elasticsearch index field
	 * @param value The value of the given field
	 * @param set The data set, uses the config file set if empty
	 * @return The number of hits for the given value in the given field
	 */
	public static Promise<Long> getTotalHits(String field, String value,
			String set) {
		String f = escapeUri(field);
		String v = escapeUri(value);
		String cacheKey = String.format("totalHits.%s.%s.%s", f, v, set);
		final Long cachedResult = (Long) Cache.get(cacheKey);
		if (cachedResult != null) {
			return Promise.promise(() -> {
				return cachedResult;
			});
		}
		return WS.url(Application.CONFIG.getString("resources.api"))
				.setQueryParameter("q", f + ":" + v)
				.setQueryParameter("set", set.equals("*") ? "" : set).get()
				.map((WSResponse response) -> {
					Long total = getTotalResults(response.asJson());
					Cache.set(cacheKey, total, Application.ONE_HOUR);
					return total;
				});
	}

	/**
	 * @param string The URI string to escape
	 * @return The URI string, escaped to be usable as an ES field or value
	 */
	public static String escapeUri(String string) {
		return string.replaceAll("([\\.:/])", "\\\\$1");
	}

	/**
	 * @param uri A Lobid-Organisation URI
	 * @return A human readable label for the given URI
	 */
	public static String organisationLabel(String uri) {
		String cacheKey = "org.label." + uri;
		String format = "short.altLabel";
		return lobidLabel(uri, cacheKey, format);
	}

	/**
	 * @param uri A Lobid-Resources URI
	 * @return A human readable label for the given URI
	 */
	public static String resourceLabel(String uri) {
		String cacheKey = "res.label." + uri;
		String format = "short.title";
		return lobidLabel(uri, cacheKey, format);
	}

	private static String lobidLabel(String uri, String cacheKey, String format) {
		final String cachedResult = (String) Cache.get(cacheKey);
		if (cachedResult != null) {
			return cachedResult;
		}
		try {
			URI.create(uri);
			String api = Application.CONFIG.getString("resources.api");
			WSRequestHolder requestHolder = WS
					.url(toApi1xOrg(
							uri.replaceAll("https?://lobid\\.org/resources?", api)))
					.setHeader("Accept", "application/json")
					.setQueryParameter("format", format);
			return requestHolder.get().map((WSResponse response) -> {
				Iterator<JsonNode> elements = response.asJson().elements();
				String label = "";
				if (elements.hasNext()) {
					label = elements.next().asText();
				} else {
					label = uri.substring(uri.lastIndexOf('/') + 1);
				}
				label = HtmlEscapers.htmlEscaper().escape(label);
				Cache.set(cacheKey, label, Application.ONE_DAY);
				return label;
			}).get(Lobid.API_TIMEOUT);
		} catch (Exception x) {
			Logger.error("Could not get Lobid label", x);
			return uri;
		}
	}

	private static String gndLabel(String uri) {
		String cacheKey = "gnd.label." + uri;
		final String cachedResult = (String) Cache.get(cacheKey);
		if (cachedResult != null) {
			return cachedResult;
		}
		WSRequestHolder requestHolder =
				WS.url(Application.CONFIG.getString("resources.api"))
						.setHeader("Accept", "application/json")
						.setQueryParameter("subject", uri)
						.setQueryParameter("format", "full").setQueryParameter("size", "1");
		return requestHolder.get().map((WSResponse response) -> {
			JsonNode value = response.asJson().get(1);
			String label = TableRow.labelForId(uri, value,
					Optional.of(Arrays.asList( // @formatter:off
							"preferredName",
					    "preferredNameForTheWork",
					    "preferredNameForTheFamily",
					    "preferredNameForThePerson",
					    "preferredNameForTheCorporateBody",
					    "preferredNameForTheSubjectHeading",
					    "preferredNameForTheConferenceOrEvent",
					    "preferredNameForThePlaceOrGeographicName"))); // @formatter:on
			Cache.set(cacheKey, label, Application.ONE_DAY);
			return label;
		}).get(Lobid.API_TIMEOUT);
	}

	/**
	 * @param q Query to search in all fields
	 * @param person Query for a person associated with the resource
	 * @param name Query for the resource name (title)
	 * @param subject Query for the resource subject
	 * @param id Query for the resource id
	 * @param publisher Query for the resource publisher
	 * @param issued Query for the resource issued year
	 * @param medium Query for the resource medium
	 * @param owner Owner filter for resource queries
	 * @param t Type filter for resource queries
	 * @param field The facet field (the field to facet over)
	 * @param set The set
	 * @param location A polygon describing the subject area of the resources
	 * @param word A word, a concept from the hbz union catalog
	 * @param corporation A corporation associated with the resource
	 * @param raw A query string that's directly (unprocessed) passed to ES
	 * @return A JSON representation of the requested facets
	 */
	public static Promise<JsonNode> getFacets(String q, String person,
			String name, String subject, String id, String publisher, String issued,
			String medium, String owner, String field, String t, String set,
			String location, String word, String corporation, String raw) {
		WSRequestHolder request =
				WS.url(Application.CONFIG.getString("resources.api") + "/facets")
						.setHeader("Accept", "application/json")
						.setQueryParameter("author", person)//
						.setQueryParameter("name", name)
						.setQueryParameter("publisher", publisher)//
						.setQueryParameter("id", id)//
						.setQueryParameter("field", field)//
						.setQueryParameter("from", "0")
						.setQueryParameter("size",
								field.equals(Application.ITEM_FIELD) ? "9999"
										: Application.MAX_FACETS + "")
						.setQueryParameter("corporation", corporation)//
						.setQueryParameter("medium", medium)//
						.setQueryParameter("location", locationPolygon(location))//
						.setQueryParameter("issued", issued)//
						.setQueryParameter("t", t)//
						.setQueryParameter("set", set);
		if (!q.isEmpty())
			request = request.setQueryParameter("word", preprocess(q));
		else if (!word.isEmpty())
			request = request.setQueryParameter("word", preprocess(word));
		if (!field.equals(Application.ITEM_FIELD))
			request = request.setQueryParameter("owner", owner);
		if (!raw.isEmpty()
				&& !raw.contains(Lobid.escapeUri(Application.COVERAGE_FIELD)))
			request = request.setQueryParameter("q", raw);
		if (!field.startsWith("http"))
			request = request.setQueryParameter("subject", subject);
		String url = request.getUrl();
		Map<String, Collection<String>> parameters = request.getQueryParameters();
		Logger.info("Facets request URL {}, query params {} ", url, parameters);
		return request.get().map((WSResponse response) -> {
			if (response.getStatus() == Http.Status.OK) {
				return response.asJson();
			}
			Logger.warn("{}: {} ({}, {})", response.getStatus(),
					response.getStatusText(), url, parameters);
			return Json.toJson(ImmutableMap.of("entries", Arrays.asList(), "field",
					field, "count", 0));
		});
	}

	private static String preprocess(final String q) {
		String result;
		if (q.trim().isEmpty() || q.matches(".*?([+~]|AND|OR|\\s-|\\*).*?")) {
			// if supported query string syntax is used, leave it alone:
			result = q;
		} else {
			// else prepend '+' to all terms for AND search:
			result = Arrays.asList(q.split("[\\s-]")).stream().map(x -> "+" + x)
					.collect(Collectors.joining(" "));
		}
		return result// but escape unsupported query string syntax:
				.replace("\\", "\\\\").replace(":", "\\:").replace("^", "\\^")
				.replace("&&", "\\&&").replace("||", "\\||").replace("!", "\\!")
				.replace("(", "\\(").replace(")", "\\)").replace("{", "\\{")
				.replace("}", "\\}").replace("[", "\\[").replace("]", "\\]")
				// `embedded` phrases, like foo"something"bar -> foo\"something\"bar
				.replaceAll("([^\\s])\"([^\"]+)\"([^\\s])", "$1\\\\\"$2\\\\\"$3")
				// remove inescapable range query symbols, possibly prepended with `+`:
				.replaceAll("^\\+?<", "").replace("^\\+?>", "");
	}

	private static final Map<String, String> keys =
			ImmutableMap.of(Application.TYPE_FIELD, "type.labels.lobid1", //
					Application.TYPE_FIELD_LOBID2, "type.labels.lobid2", //
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
	 *          `"Eisenbahnlinie,http://d-nb.info/gnd/4129465-8"`
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
		else if (uris.size() == 1 && isGnd(uris.get(0)))
			return Lobid.gndLabel(uris.get(0));
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
		if ((uris.size() == 1 && isOrg(uris.get(0)))
				|| field.equals(Application.ITEM_FIELD))
			return "octicon octicon-home";
		else if ((uris.size() == 1 && field.equals(Application.COVERAGE_FIELD)))
			return "octicon octicon-milestone";
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

	static String toApi1xOrg(String url) {
		String betaOrgsPrefix = "http://beta.lobid.org/organisations";
		return url.startsWith(betaOrgsPrefix)
				? url.replace(betaOrgsPrefix, "http://lobid.org/organisation") : url;
	}

	static boolean isOrg(String term) {
		return term.contains("lobid.org/organisation");
	}

	private static boolean isGnd(String term) {
		return term.startsWith("http://d-nb.info/gnd");
	}

	private static String locationPolygon(String location) {
		return location.contains("|") ? location.split("\\|")[1] : location;
	}

	/**
	 * @param doc The result JSON doc
	 * @return A mapping of ISILs to item URIs
	 */
	public static Map<String, List<String>> items(String doc) {
		JsonNode items = Json.parse(doc).findValue("exemplar");
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
			String itemUri = elements.next().asText();
			try {
				String isil = itemUri.split(":")[2];
				List<String> uris = result.getOrDefault(isil, new ArrayList<>());
				uris.add(itemUri);
				result.put(isil, uris);
			} catch (ArrayIndexOutOfBoundsException x) {
				Logger.error(x.getMessage());
			}
		}
	}

	/**
	 * @param itemUri The lobid item URI
	 * @return The OPAC URL for the given item, or null
	 */
	public static String opacUrl(String itemUri) {
		try (InputStream stream =
				Play.application().resourceAsStream("isil2opac_hbzid.json")) {
			JsonNode json = Json.parse(stream);
			String[] hbzId_isil_sig =
					itemUri.substring(itemUri.indexOf("item/") + 5).split(":");
			String hbzId = hbzId_isil_sig[0];
			String isil = hbzId_isil_sig[1];
			Logger.debug("From item URI {}, got ISIL {} and HBZ-ID {}", itemUri, isil,
					hbzId);
			JsonNode urlTemplate = json.get(isil);
			if (urlTemplate != null)
				return urlTemplate.asText().replace("{hbzid}", hbzId);
		} catch (IOException e) {
			Logger.error("Could not create OPAC URL", e);
		}
		return null;
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
		} else if (all1[1].equals(all2[1])) {
			// same main sigel, prefer shorter, e.g. DE-5 before DE-5-11
			return all1.length < all2.length;
		}
		// compare by main sigel, e.g. DE-5 before DE-6:
		return numerical(all1[1]) < numerical(all2[1]);
	}

	private static int numerical(String s) {
		// replace non-digits with 9, e.g. for DE-5 before DE-Walb1
		return Integer.parseInt(s.replaceAll("\\D", "9"));
	}
}
