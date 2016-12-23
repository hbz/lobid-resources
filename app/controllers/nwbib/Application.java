/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Spliterators;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.common.geo.GeoPoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import play.Logger;
import play.cache.Cache;
import play.cache.Cached;
import play.data.Form;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import views.html.details;
import views.html.index;
import views.html.search;
import views.html.stars;

/**
 * The main application controller.
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class Application extends Controller {

	static final int MAX_FACETS = 150;

	private static final String STARRED = "starred";

	/** The internal ES field for the type facet. */
	public static final String TYPE_FIELD = "@graph.@type";
	/** The type field in Lobid data 2.0. */
	public static final String TYPE_FIELD_LOBID2 = "type";
	/** The internal ES field for the medium facet. */
	public static final String MEDIUM_FIELD =
			"@graph.http://purl.org/dc/terms/medium.@id";
	/** The internal ES field for the item/exemplar facet. */
	public static final String ITEM_FIELD =
			"@graph.http://purl.org/vocab/frbr/core#exemplar.@id";

	/** The internal ES field for the coverage facet. */
	public static final String COVERAGE_FIELD =
			"@graph.http://purl.org/dc/elements/1.1/coverage.@value.raw";
	/** The internal ES field for subject locations. */
	public static final String SUBJECT_LOCATION_FIELD =
			"@graph.http://purl.org/lobid/lv#subjectLocation.@value";

	/** The internal ES field for subjects. */
	public static final String SUBJECT_FIELD =
			"@graph.http://purl.org/dc/terms/subject.@id";

	/** The internal ES field for issued years. */
	public static final String ISSUED_FIELD =
			"@graph.http://purl.org/dc/terms/issued.@value";

	private static final File FILE = new File("conf/nwbib.conf");
	/** Access to the nwbib.conf config file. */
	public final static Config CONFIG = ConfigFactory
			.parseFile(
					FILE.exists() ? FILE : new File("modules/nwbib/conf/nwbib.conf"))
			.resolve();

	static Form<String> queryForm = Form.form(String.class);

	static final int ONE_HOUR = 60 * 60;
	/** The number of seconds in one day. */
	public static final int ONE_DAY = 24 * ONE_HOUR;

	/**
	 * @return The NWBib index page.
	 */
	public static Result index() {
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors())
			return badRequest(index.render());
		return ok(index.render());
	}

	/**
	 * @return The NWBib advanced search page.
	 */
	@Cached(key = "nwbib.advanced", duration = ONE_HOUR)
	public static Result advanced() {
		return ok(views.html.advanced.render());
	}

	/**
	 * @return The current full URI, URL-encoded, or null.
	 */
	public static String currentUri() {
		try {
			return URLEncoder.encode(request().host() + request().uri(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			Logger.error("Could not get current URI", e);
		}
		return null;
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
	 * @param from The page start (offset of page of resource to return)
	 * @param size The page size (size of page of resource to return)
	 * @param owner Owner filter for resource queries
	 * @param t Type filter for resource queries
	 * @param sort Sorting order for results ("newest", "oldest", "" -> relevance)
	 * @param details If true, render details
	 * @param set The set, overrides the default NWBib set if not empty
	 * @param location A polygon describing the subject area of the resources
	 * @param word A word, a concept from the hbz union catalog
	 * @param corporation A corporation associated with the resource
	 * @param raw A query string that's directly (unprocessed) passed to ES
	 * @return The search results
	 */
	public static Promise<Result> search(final String q, final String person,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final int from, final int size, final String owner, String t, String sort,
			boolean details, String set, String location, String word,
			String corporation, String raw) {
		String uuid = session("uuid");
		if (uuid == null)
			session("uuid", UUID.randomUUID().toString());
		String cacheId = String.format("%s-%s", uuid, request().uri());
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		Logger.debug("Not cached: {}, will cache for one hour", cacheId);
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors())
			return Promise.promise(() -> badRequest(search.render(null, q, person,
					name, subject, id, publisher, issued, medium, from, size, 0L, owner,
					t, sort, set, location, word, corporation, raw)));
		String query = form.data().get("q");
		Promise<Result> result = okPromise(query != null ? query : q, person, name,
				subject, id, publisher, issued, medium, from, size, owner, t, sort,
				details, set, location, word, corporation, raw);
		cacheOnRedeem(cacheId, result, ONE_HOUR);
		return result;
	}

	/**
	 * @param id The resource ID.
	 * @return The details page for the resource with the given ID.
	 */
	public static Promise<Result> show(final String id) {
		String prevNext = (String) Cache.get(session("uuid") + "-" + id);
		if (prevNext != null) {
			session("prev", prevNext.startsWith(",") ? "" : prevNext.split(",")[0]);
			session("next", prevNext.endsWith(",") ? "" : prevNext.split(",")[1]);
		} else {
			Logger.warn("No pagination session data for {}", id);
		}
		return search("", "", "", "", id, "", "", "", 0, 1, "", "", "", true, "",
				"", "", "", "");
	}

	private static Promise<Result> okPromise(final String q, final String person,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final int from, final int size, final String owner, String t, String sort,
			boolean details, String set, String location, String word,
			String corporation, String raw) {
		final Promise<Result> result = call(q, person, name, subject, id, publisher,
				issued, medium, from, size, owner, t, sort, details, set, location,
				word, corporation, raw);
		return result.recover((Throwable throwable) -> {
			Logger.error("Could not call Lobid", throwable);
			flashError();
			return internalServerError(search.render("[]", q, person, name, subject,
					id, publisher, issued, medium, from, size, 0L, owner, t, sort, set,
					location, word, corporation, raw));
		});
	}

	private static void flashError() {
		flash("error",
				"Es ist ein Fehler aufgetreten. "
						+ "Bitte versuchen Sie es erneut oder kontaktieren Sie das "
						+ "Entwicklerteam, falls das Problem fortbesteht "
						+ "(siehe Link 'Feedback' oben rechts).");
	}

	private static void cacheOnRedeem(final String cacheId,
			final Promise<Result> resultPromise, final int duration) {
		resultPromise.onRedeem((Result result) -> {
			if (play.test.Helpers.status(result) == Http.Status.OK)
				Cache.set(cacheId, resultPromise, duration);
		});
	}

	static Promise<Result> call(final String q, final String person,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final int from, final int size, String owner, String t, String sort,
			boolean showDetails, String set, String location, String word,
			String corporation, String raw) {
		final WSRequestHolder requestHolder =
				Lobid.request(q, person, name, subject, id, publisher, issued, medium,
						from, size, owner, t, sort, set, location, word, corporation, raw);
		return requestHolder.get().map((WSResponse response) -> {
			Long hits = 0L;
			String s = "{}";
			if (response.getStatus() == Http.Status.OK) {
				JsonNode json = response.asJson();
				hits = Lobid.getTotalResults(json);
				s = json.toString();
			} else {
				Logger.warn("{}: {} ({}, {})", response.getStatus(),
						response.getStatusText(), requestHolder.getUrl(),
						requestHolder.getQueryParameters());
			}
			if (showDetails) {
				String json = "";
				JsonNode nodes = Json.parse(s);
				if (nodes.isArray() && nodes.size() == 2) { // first: metadata
					json = nodes.get(1).toString();
				} else {
					Logger.warn("No suitable data to show details for: {}", nodes);
				}
				return ok(details.render(CONFIG, json, id));
			}
			return ok(search.render(s, q, person, name, subject, id, publisher,
					issued, medium, from, size, hits, owner, t, sort, set, location, word,
					corporation, raw));
		});
	}

	private static void uncache(List<String> ids) {
		for (String id : ids) {
			Cache.remove(String.format("%s-/nwbib/%s", session("uuid"), id));
		}
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
	 * @param from The page start (offset of page of resource to return)
	 * @param size The page size (size of page of resource to return)
	 * @param owner Owner filter for resource queries
	 * @param t Type filter for resource queries
	 * @param field The facet field (the field to facet over)
	 * @param sort Sorting order for results ("newest", "oldest", "" -> relevance)
	 * @param set The set, overrides the default NWBib set if not empty
	 * @param location A polygon describing the subject area of the resources
	 * @param word A word, a concept from the hbz union catalog
	 * @param corporation A corporation associated with the resource
	 * @param raw A query string that's directly (unprocessed) passed to ES
	 * @return The search results
	 */
	public static Promise<Result> facets(String q, String person, String name,
			String subject, String id, String publisher, String issued, String medium,
			int from, int size, String owner, String t, String field, String sort,
			String set, String location, String word, String corporation,
			String raw) {

		String key =
				String.format("facets.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s",
						field, q, person, name, id, publisher, set, location, word,
						corporation, raw, subject, issued, medium, owner, t);
		Result cachedResult = (Result) Cache.get(key);
		if (cachedResult != null) {
			return Promise.promise(() -> cachedResult);
		}

		String labelTemplate = "<span class='%s'/>&nbsp;%s (%s)";

		Function<JsonNode, Pair<JsonNode, String>> toLabel = json -> {
			String term = json.get("term").asText();
			int count = json.get("count").asInt();
			String icon = Lobid.facetIcon(Arrays.asList(term), field);
			String label = Lobid.facetLabel(Arrays.asList(term), field, "");
			String fullLabel = String.format(labelTemplate, icon, label, count);
			return Pair.of(json, fullLabel);
		};

		Predicate<Pair<JsonNode, String>> labelled = pair -> {
			JsonNode json = pair.getLeft();
			String label = pair.getRight();
			int count = json.get("count").asInt();
			return (!label.contains("http") || label.contains("nwbib")) && label
					.length() > String.format(labelTemplate, "", "", count).length();
		};

		Collator collator = Collator.getInstance(Locale.GERMAN);
		Comparator<Pair<JsonNode, String>> sorter = (p1, p2) -> {
			String t1 = p1.getLeft().get("term").asText();
			String t2 = p2.getLeft().get("term").asText();
			boolean t1Current = current(subject, medium, owner, t, field, t1, raw);
			boolean t2Current = current(subject, medium, owner, t, field, t2, raw);
			if (t1Current == t2Current) {
				if (!field.equals(ISSUED_FIELD)) {
					Integer c1 = p1.getLeft().get("count").asInt();
					Integer c2 = p2.getLeft().get("count").asInt();
					return c2.compareTo(c1);
				}
				String l1 = p1.getRight().substring(p1.getRight().lastIndexOf('>') + 1);
				String l2 = p2.getRight().substring(p2.getRight().lastIndexOf('>') + 1);
				return collator.compare(l1, l2);
			}
			return t1Current ? -1 : t2Current ? 1 : 0;
		};

		Function<Pair<JsonNode, String>, String> toHtml = pair -> {
			JsonNode json = pair.getLeft();
			String fullLabel = pair.getRight();
			String term = json.get("term").asText();
			if (field.equals(SUBJECT_LOCATION_FIELD)) {
				GeoPoint point = new GeoPoint(term);
				term = String.format("%s,%s", point.getLat(), point.getLon());
			}
			String mediumQuery = !field.equals(MEDIUM_FIELD) //
					? medium : queryParam(medium, term);
			String typeQuery = !field.equals(TYPE_FIELD) //
					? t : queryParam(t, term);
			String ownerQuery = !field.equals(ITEM_FIELD) //
					? owner : withoutAndOperator(queryParam(owner, term));
			String rawQuery = !field.equals(COVERAGE_FIELD) //
					? raw : rawQueryParam(raw, term);
			String locationQuery = !field.equals(SUBJECT_LOCATION_FIELD) //
					? location : term;
			String subjectQuery = !field.equals(SUBJECT_FIELD) //
					? subject : queryParam(subject, term);
			String issuedQuery = !field.equals(ISSUED_FIELD) //
					? issued : queryParam(issued, term);

			boolean current = current(subject, medium, owner, t, field, term, raw);

			String routeUrl = routes.Application.search(q, person, name, subjectQuery,
					id, publisher, issuedQuery, mediumQuery, from, size, ownerQuery,
					typeQuery, sort(sort, subjectQuery), false, set, locationQuery, word,
					corporation, rawQuery).url();

			String result = String.format(
					"<li " + (current ? "class=\"active\"" : "")
							+ "><a class=\"%s-facet-link\" href='%s'>"
							+ "<input onclick=\"location.href='%s'\" class=\"facet-checkbox\" "
							+ "type=\"checkbox\" %s>&nbsp;%s</input>" + "</a></li>",
					Math.abs(field.hashCode()), routeUrl, routeUrl,
					current ? "checked" : "", fullLabel);

			return result;
		};

		Promise<Result> promise = Lobid
				.getFacets(q, person, name, subject, id, publisher, issued, medium,
						owner, field, t, set, location, word, corporation, raw)
				.map(json -> {
					Stream<JsonNode> stream =
							StreamSupport.stream(Spliterators.spliteratorUnknownSize(
									json.findValue("entries").elements(), 0), false);
					if (field.equals(ITEM_FIELD)) {
						stream = preprocess(stream);
					}
					String labelKey = String.format(
							"facets-labels.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s",
							field, raw, q, person, name, id, publisher, set, word,
							corporation, subject, issued, medium, raw,
							field.equals(ITEM_FIELD) ? "" : owner, t, location);

					@SuppressWarnings("unchecked")
					List<Pair<JsonNode, String>> labelledFacets =
							(List<Pair<JsonNode, String>>) Cache.get(labelKey);
					if (labelledFacets == null) {
						labelledFacets = stream.map(toLabel).filter(labelled)
								.collect(Collectors.toList());
						Cache.set(labelKey, labelledFacets, ONE_DAY);
					}
					return labelledFacets.stream().sorted(sorter).map(toHtml)
							.collect(Collectors.toList());
				}).map(lis -> ok(String.join("\n", lis)));
		promise.onRedeem(r -> Cache.set(key, r, ONE_DAY));
		return promise;
	}

	private static String sort(String sort, String subjectQuery) {
		return subjectQuery.contains(",") ? "" /* relevance */ : sort;
	}

	private static boolean current(String subject, String medium, String owner,
			String t, String field, String term, String raw) {
		return field.equals(MEDIUM_FIELD) && contains(medium, term)
				|| field.equals(TYPE_FIELD) && contains(t, term)
				|| field.equals(ITEM_FIELD) && contains(owner, term)
				|| field.equals(COVERAGE_FIELD) && rawContains(raw, quotedEscaped(term))
				|| field.equals(SUBJECT_FIELD) && contains(subject, term);
	}

	private static boolean contains(String value, String term) {
		return Arrays.asList(value.split(",")).contains(term);
	}

	private static String queryParam(String currentParam, String term) {
		if (currentParam.isEmpty())
			return term;
		else if (contains(currentParam, term)) {
			String termRemoved = currentParam.replace(term, "")
					.replaceAll("\\A,|,?\\z", "").replaceAll(",+", ",");
			return termRemoved.equals("AND") ? "" : termRemoved;
		} else
			return withoutAndOperator(currentParam) + "," + term + ",AND";
	}

	private static String withoutAndOperator(String currentParam) {
		return currentParam.replace(",AND", "");
	}

	/**
	 * @param currentParam The current value of the query param
	 * @param term The term to create a query for
	 * @return The escaped Elasticsearch query string for the `raw` query param
	 */
	public static String rawQueryParam(String currentParam, String term) {
		String rawPrefix =
				Lobid.escapeUri(COVERAGE_FIELD.replace(".raw", "")) + ":";
		if (currentParam.isEmpty()) {
			return rawPrefix + "(+" + quotedEscaped(term) + ")";
		} else if (rawContains(currentParam, quotedEscaped(term))) {
			String removedTerm = currentParam.replace(rawPrefix, "")
					.replace("+" + quotedEscaped(term), "")
					.replaceAll("\\A\\+|\\+\\z", "").replaceAll("\\++", "+");
			return removedTerm.trim().equals("()") ? "" : rawPrefix + removedTerm;
		} else
			return currentParam.substring(0, currentParam.length() - 1) + "+"
					+ quotedEscaped(term) + ")";
	}

	private static String quotedEscaped(String term) {
		return "\"" + Lobid.escapeUri(term) + "\"";
	}

	private static boolean rawContains(String raw, String term) {
		String[] split = raw.split(":");
		String terms = split[split.length - 1];
		terms =
				terms.length() >= 2 ? terms.substring(1, terms.length() - 1) : terms;
		return Arrays.asList(terms.split("\\+")).contains(term);
	}

	private static Stream<JsonNode> preprocess(Stream<JsonNode> stream) {
		String captureItemUriWithoutSignature =
				"(http://lobid\\.org/item/[^:]*?:[^:]*?:)[^\"]*";
		List<String> itemUrisWithoutSignatures = stream
				.map(json -> json.get("term").asText()
						.replaceAll(captureItemUriWithoutSignature, "$1"))
				.distinct().collect(Collectors.toList());
		return count(itemUrisWithoutSignatures).entrySet().stream()
				.map(entry -> Json.toJson(ImmutableMap.of(//
						"term",
						Lobid.toApi1xOrg(Application.CONFIG.getString("orgs.api")) + "/"
								+ entry.getKey(), //
						"count", entry.getValue())));
	}

	private static Map<String, Integer> count(List<String> itemUris) {
		String captureIsilOrgIdentifier =
				"http://lobid\\.org/item/[^:]*?:([^:]*?):[^\"]*";
		Map<String, Integer> map = new HashMap<>();
		for (String term : itemUris) {
			String isil = term.replaceAll(captureIsilOrgIdentifier, "$1");
			if (!isil.trim().isEmpty())
				map.put(isil, map.get(isil) == null ? 1 : map.get(isil) + 1);
		}
		return map;
	}

	/**
	 * @param id The resource ID
	 * @return True, if the resource with given ID is starred by the user
	 */
	public static boolean isStarred(String id) {
		return starredIds().contains(id);
	}

	/**
	 * @param id The resource ID to star
	 * @return An OK result
	 */
	public static Result star(String id) {
		String starred = currentlyStarred();
		if (!starred.contains(id)) {
			session(STARRED, starred + " " + id);
			uncache(Arrays.asList(id));
		}
		return ok("Starred: " + id);
	}

	/**
	 * @param ids The resource IDs to star
	 * @return A 303 SEE_OTHER result to the referrer
	 */
	public static Result starAll(String ids) {
		Arrays.asList(ids.split(",")).forEach(id -> star(id));
		return seeOther(request().getHeader(REFERER));
	}

	/**
	 * @param id The resource ID to unstar
	 * @return An OK result
	 */
	public static Result unstar(String id) {
		List<String> starred = starredIds();
		starred.remove(id);
		session(STARRED, String.join(" ", starred));
		uncache(Arrays.asList(id));
		return ok("Unstarred: " + id);
	}

	/**
	 * @param format The format to show the current stars in
	 * @param ids Comma-separated IDs to show, of empty string
	 * @return A page with all resources starred by the user
	 */
	public static Promise<Result> showStars(String format, String ids) {
		final List<String> starred = starredIds();
		if (ids.isEmpty() && !starred.isEmpty()) {
			return Promise.pure(redirect(routes.Application.showStars(format,
					starred.stream().collect(Collectors.joining(",")))));
		}
		final List<String> starredIds = starred.isEmpty() && ids.trim().isEmpty()
				? starred : Arrays.asList(ids.split(","));
		String cacheKey = "starsForIds." + starredIds;
		Object cachedJson = Cache.get(cacheKey);
		if (cachedJson != null && cachedJson instanceof List) {
			@SuppressWarnings("unchecked")
			List<JsonNode> json = (List<JsonNode>) cachedJson;
			return Promise.pure(ok(stars.render(starredIds, json, format)));
		}
		Stream<Promise<JsonNode>> promises = starredIds.stream()
				.map(id -> WS
						.url(String.format("http://lobid.org/resource/%s?format=full", id))
						.get().map(response -> response.asJson()));
		return Promise.sequence(promises.collect(Collectors.toList()))
				.map((List<JsonNode> vals) -> {
					uncache(starredIds);
					Cache.set(cacheKey, vals, ONE_DAY);
					return ok(stars.render(starredIds, vals, format));
				});
	}

	/**
	 * @param ids The ids of the resources to unstar, or empty string to clear all
	 * @return If ids is empty: an OK result to confirm deletion of all starred
	 *         resources; if ids are given: A 303 SEE_OTHER result to the referrer
	 */
	public static Result clearStars(String ids) {
		if (ids.isEmpty()) {
			uncache(starredIds());
			session(STARRED, "");
			return ok(stars.render(starredIds(), Collections.emptyList(), ""));
		}
		Arrays.asList(ids.split(",")).forEach(id -> unstar(id));
		return seeOther(request().getHeader(REFERER));
	}

	/**
	 * @param path The path to redirect to
	 * @return A 301 MOVED_PERMANENTLY redirect to the path
	 */
	public static Result redirect(String path) {
		return movedPermanently("/" + path);
	}

	/**
	 * @return The space-delimited IDs of the currently starred resouces
	 */
	public static String currentlyStarred() {
		String starred = session(STARRED);
		return starred == null ? "" : starred.trim();
	}

	private static List<String> starredIds() {
		return new ArrayList<>(Arrays.asList(currentlyStarred().split(" ")).stream()
				.filter(s -> !s.trim().isEmpty()).collect(Collectors.toList()));
	}
}
