package controllers.nwbib;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import play.Logger;
import play.cache.Cache;
import play.libs.F.Promise;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

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

	static WSRequestHolder request(final String q, final String author,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final String nwbibspatial, final String nwbibsubject, final int from,
			final int size, String owner, String t, String sort) {
		WSRequestHolder requestHolder =
				WS.url(Application.CONFIG.getString("nwbib.api"))
						.setHeader("Accept", "application/json")
						.setQueryParameter("set", Application.CONFIG.getString("nwbib.set"))
						.setQueryParameter("format", "full")
						.setQueryParameter("from", from + "")
						.setQueryParameter("size", size + "")
						.setQueryParameter("sort", sort);
		if (!q.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("q", preprocess(q));
		if (!author.trim().isEmpty())
			requestHolder = requestHolder.setQueryParameter("author", author);
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
		if (!nwbibspatial.trim().isEmpty())
			requestHolder =
					requestHolder.setQueryParameter("nwbibspatial", nwbibspatial);
		if (!nwbibsubject.trim().isEmpty())
			requestHolder =
					requestHolder.setQueryParameter("nwbibsubject", nwbibsubject);
		if (!owner.equals("all"))
			requestHolder = requestHolder.setQueryParameter("owner", owner);
		if (!t.isEmpty())
			requestHolder = requestHolder.setQueryParameter("t", t);
		Logger.info("Request URL {}, query params {} ", requestHolder.getUrl(),
				requestHolder.getQueryParameters());
		return requestHolder;
	}

	/** @return The full number of hits, ie. the size of our data set. */
	public static Promise<Long> getTotalHits() {
		final Long cachedResult = (Long) Cache.get(String.format("totalHits"));
		if (cachedResult != null) {
			return Promise.promise(() -> {
				return cachedResult;
			});
		}
		WSRequestHolder requestHolder =
				request("", "", "", "", "", "", "", "", "", "", 0, 0, "", "", "");
		return requestHolder.get().map((WSResponse response) -> {
			Long total = getTotalResults(response.asJson());
			Cache.set("totalHits", total, Application.ONE_HOUR);
			return total;
		});
	}

	/**
	 * @param uri A Lobid-Organisation URI
	 * @return A human readable label for the given URI
	 */
	public static String organisationLabel(String uri) {
		final String cachedResult =
				(String) Cache.get(String.format("org.label." + uri));
		if (cachedResult != null) {
			return cachedResult;
		}
		WSRequestHolder requestHolder =
				WS.url(uri).setHeader("Accept", "application/json")
						.setQueryParameter("format", "short.name");
		return requestHolder.get().map((WSResponse response) -> {
			String label = response.asJson().elements().next().asText();
			Cache.set("org.label." + uri, label, Application.ONE_HOUR);
			return label;
		}).get(10000);
	}

	/**
	 * @param q Query to search in all fields
	 * @param author Query for the resource author
	 * @param name Query for the resource name (title)
	 * @param subject Query for the resource subject
	 * @param id Query for the resource id
	 * @param publisher Query for the resource author
	 * @param issued Query for the resource issued year
	 * @param medium Query for the resource medium
	 * @param nwbibspatial Query for the resource nwbibspatial classification
	 * @param nwbibsubject Query for the resource nwbibsubject classification
	 * @param owner Owner filter for resource queries
	 * @param t Type filter for resource queries
	 * @param field The facet field (the field to facet over)
	 * @return A JSON representation of the requested facets
	 */
	public static Promise<JsonNode> getFacets(String q, String author,
			String name, String subject, String id, String publisher, String issued,
			String medium, String nwbibspatial, String nwbibsubject, String owner,
			String field, String t) {
		WSRequestHolder requestHolder =
				WS.url(Application.CONFIG.getString("nwbib.api") + "/facets")
						.setHeader("Accept", "application/json")
						.setQueryParameter("set", Application.CONFIG.getString("nwbib.set"))
						.setQueryParameter("q", preprocess(q))
						.setQueryParameter("author", author)
						.setQueryParameter("name", name)
						.setQueryParameter("subject", subject)
						.setQueryParameter("publisher", publisher)
						.setQueryParameter("issued", issued).setQueryParameter("id", id)
						.setQueryParameter("owner", owner.equals("all") ? "" : owner)
						.setQueryParameter("field", field)
						.setQueryParameter("nwbibspatial", nwbibspatial)
						.setQueryParameter("nwbibsubject", nwbibsubject);
		if (!field.equals(Application.MEDIUM_FIELD))
			requestHolder = requestHolder.setQueryParameter("medium", medium);
		if (!field.equals(Application.TYPE_FIELD))
			requestHolder = requestHolder.setQueryParameter("t", t);
		Logger.info("Facets request URL {}, query params {} ",
				requestHolder.getUrl(), requestHolder.getQueryParameters());
		return requestHolder.get().map((WSResponse response) -> {
			return response.asJson();
		});
	}

	private static String preprocess(final String q) {
		return // if query string syntax is used, leave it alone:
		q.matches(".*?([+\\-~]|AND|OR).*?") ? q :
		// else prepend '+' to all terms for AND search:
				Arrays.asList(q.split(" ")).stream().map(x -> "+" + x)
						.collect(Collectors.joining(" "));
	}

	private static final Map<String, String> keys = ImmutableMap.of(
			Application.TYPE_FIELD, "type.labels",//
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
		return facetLabel(types, Application.TYPE_FIELD);
	}

	/**
	 * @param uris Some URIs
	 * @param field The ES field to facet over
	 * @return A human readable label for the URIs
	 */
	public static String facetLabel(List<String> uris, String field) {
		String configKey = keys.get(field);
		String type = selectType(uris, configKey);
		if (type.isEmpty())
			return "";
		@SuppressWarnings("unchecked")
		List<String> details =
				((List<String>) Application.CONFIG.getObject(configKey).unwrapped()
						.get(type));
		if (details == null)
			return type;
		String selected = details.get(0);
		return selected.isEmpty() ? uris.get(0) : selected;
	}

	/**
	 * @param uris Some URIs
	 * @param field The ES field to facet over
	 * @return An icon CSS class for the given URIs
	 */
	public static String facetIcon(List<String> uris, String field) {
		String configKey = keys.get(field);
		String type = selectType(uris, configKey);
		if (type.isEmpty())
			return "";
		@SuppressWarnings("unchecked")
		List<String> details =
				(List<String>) Application.CONFIG.getObject(configKey).unwrapped()
						.get(type);
		if (details == null)
			return type;
		String selected = details.get(1);
		return selected.isEmpty() ? uris.get(0) : selected;
	}

	private static String selectType(List<String> types, String configKey) {
		Logger.trace("Types: " + types);
		@SuppressWarnings("unchecked")
		List<String> selected =
				types
						.stream()
						.map(
								t -> {
									List<String> vals =
											((List<String>) Application.CONFIG.getObject(configKey)
													.unwrapped().get(t));
									if (vals == null)
										return t;
									return vals.get(0).isEmpty() || vals.get(1).isEmpty() ? ""
											: t;
								}).filter(t -> {
							return !t.isEmpty();
						}).collect(Collectors.toList());
		Collections.sort(selected);
		Logger.trace("Selected: " + selected);
		return selected.isEmpty() ? "" : selected.get(0).contains("Miscellaneous")
				&& selected.size() > 1 ? selected.get(1) : selected.get(0);
	}
}
