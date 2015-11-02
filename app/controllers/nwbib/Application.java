/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.collect.Iterators;
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
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import views.html.browse_classification;
import views.html.browse_register;
import views.html.classification;
import views.html.details;
import views.html.help;
import views.html.index;
import views.html.register;
import views.html.search;
import views.html.stars;

/**
 * The main application controller.
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class Application extends Controller {

	static final int MAX_FACETS = 9999;

	private static final String STARRED = "starred";

	/** The internal ES field for the type facet. */
	public static final String TYPE_FIELD = "@graph.@type";
	/** The internal ES field for the medium facet. */
	public static final String MEDIUM_FIELD =
			"@graph.http://purl.org/dc/terms/medium.@id";
	/** The internal ES field for the item/exemplar facet. */
	public static final String ITEM_FIELD =
			"@graph.http://purl.org/vocab/frbr/core#exemplar.@id";

	/** The internal ES field for the NWBib subject facet. */
	public static final String NWBIB_SUBJECT_FIELD =
			"@graph.http://purl.org/lobid/lv#nwbibsubject.@id";
	/** The internal ES field for the NWBib spatial facet. */
	public static final String NWBIB_SPATIAL_FIELD =
			"@graph.http://purl.org/lobid/lv#nwbibspatial.@id";
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

	/** Access to the NWBib classification data stored in ES. */
	public final static Classification CLASSIFICATION = new Classification(
			CONFIG.getString("nwbib.cluster"), CONFIG.getString("nwbib.server"));

	static final int ONE_HOUR = 60 * 60;
	/** The number of seconds in one day. */
	public static final int ONE_DAY = 24 * ONE_HOUR;

	/**
	 * @return The NWBib index page.
	 */
	@Cached(key = "nwbib.index", duration = ONE_HOUR)
	public static Result index() {
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors())
			return badRequest(index.render());
		return ok(index.render());
	}

	/**
	 * @return The NWBib help page.
	 */
	@Cached(key = "nwbib.help", duration = ONE_HOUR)
	public static Result help() {
		return ok(help.render());
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
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param q The topics query.
	 * @return The NWBib topics search page.
	 */
	public static Promise<Result> topics(String q) {
		if (q.isEmpty())
			return Promise.promise(() -> ok(views.html.topics.render(q, "")));
		String cacheId = "topics." + q;
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		WSRequestHolder requestHolder = Lobid.topicRequest(q);
		Promise<Result> result = requestHolder.get().map((WSResponse response) -> {
			JsonNode json = response.asJson();
			String s = json.toString();
			return ok(views.html.topics.render(q, s));
		});
		cacheOnRedeem(cacheId, result, ONE_HOUR);
		return result;
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
	 * @param nwbibspatial Query for the resource nwbibspatial classification
	 * @param nwbibsubject Query for the resource nwbibsubject classification
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
			final String nwbibspatial, final String nwbibsubject, final int from,
			final int size, final String owner, String t, String sort,
			boolean details, String set, String location, String word,
			String corporation, String raw) {
		String uuid = session("uuid");
		if (uuid == null)
			session("uuid", UUID.randomUUID().toString());
		if (id.isEmpty()) {
			session("lastSearchUrl", request().uri());
			response().setHeader("Cache-Control",
					"no-cache, no-store, must-revalidate");
			response().setHeader("Pragma", "no-cache");
			response().setHeader("Expires", "0");
		}
		String cacheId = String.format("%s-%s", uuid, request().uri());
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		Logger.debug("Not cached: {}, will cache for one hour", cacheId);
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors())
			return Promise.promise(
					() -> badRequest(search.render(null, q, person, name, subject, id,
							publisher, issued, medium, nwbibspatial, nwbibsubject, from, size,
							0L, owner, t, sort, set, location, word, corporation, raw)));
		String query = form.data().get("q");
		Promise<Result> result =
				okPromise(query != null ? query : q, person, name, subject, id,
						publisher, issued, medium, nwbibspatial, nwbibsubject, from, size,
						owner, t, sort, details, set, location, word, corporation, raw);
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
		return search("", "", "", "", id, "", "", "", "", "", 0, 1, "", "", "",
				true, "", "", "", "", "");
	}

	/**
	 * @param q The query
	 * @param callback The JSONP callback
	 * @param t The type filter ("Raumsystematik" or "Sachsystematik")
	 * @return Subject data for the given query
	 */
	public static Promise<Result> subject(final String q, final String callback,
			final String t) {
		String cacheId = String.format("%s.%s.%s.%s", "subject", q, callback, t);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		Logger.debug("Not cached: {}, will cache for one day", cacheId);
		Promise<JsonNode> jsonPromise =
				Promise.promise(() -> CLASSIFICATION.ids(q, t));
		Promise<Result> result;
		if (!callback.isEmpty())
			result = jsonPromise.map((JsonNode json) -> ok(
					String.format("%s(%s)", callback, Json.stringify(json))));
		else
			result = jsonPromise.map((JsonNode json) -> ok(json));
		cacheOnRedeem(cacheId, result, ONE_DAY);
		return result;
	}

	/**
	 * @param t The register type ("Raumsystematik" or "Sachsystematik")
	 * @return The alphabetical register for the given classification type
	 */
	public static Result register(final String t) {
		Result cachedResult = (Result) Cache.get("register." + t);
		if (cachedResult != null)
			return cachedResult;
		Result result = null;
		if (t.isEmpty()) {
			result = ok(register.render());
		} else {
			SearchResponse response = CLASSIFICATION.dataFor(t);
			if (response == null) {
				Logger.error("Failed to get data for register type: " + t);
				flashError();
				return internalServerError(browse_register.render(null, t));
			}
			JsonNode sorted = CLASSIFICATION.sorted(response);
			result = ok(browse_register.render(sorted.toString(), t));
		}
		Cache.set("result." + t, result, ONE_DAY);
		return result;
	}

	/**
	 * @param t The register type ("Raumsystematik" or "Sachsystematik")
	 * @return Classification data for the given type
	 */
	public static Result classification(final String t) {
		Result cachedResult = (Result) Cache.get("classification." + t);
		if (cachedResult != null)
			return cachedResult;
		Result result = null;
		if (t.isEmpty()) {
			result = ok(classification.render());
		} else {
			SearchResponse response = CLASSIFICATION.dataFor(t);
			if (response == null) {
				Logger.error("Failed to get data for classification type: " + t);
				flashError();
				return internalServerError(browse_classification.render(null, null, t));
			}
			result = classificationResult(response, t);
		}
		Cache.set("classification." + t, result, ONE_DAY);
		return result;
	}

	private static Result classificationResult(SearchResponse response,
			String t) {
		List<JsonNode> topClasses = new ArrayList<>();
		Map<String, List<JsonNode>> subClasses = new HashMap<>();
		CLASSIFICATION.buildHierarchy(response, topClasses, subClasses);
		String topClassesJson = Json.toJson(topClasses).toString();
		return ok(browse_classification.render(topClassesJson, subClasses, t));
	}

	private static Promise<Result> okPromise(final String q, final String person,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final String nwbibspatial, final String nwbibsubject, final int from,
			final int size, final String owner, String t, String sort,
			boolean details, String set, String location, String word,
			String corporation, String raw) {
		final Promise<Result> result = call(q, person, name, subject, id, publisher,
				issued, medium, nwbibspatial, nwbibsubject, from, size, owner, t, sort,
				details, set, location, word, corporation, raw);
		return result.recover((Throwable throwable) -> {
			throwable.printStackTrace();
			flashError();
			return internalServerError(search.render("[]", q, person, name, subject,
					id, publisher, issued, medium, nwbibspatial, nwbibsubject, from, size,
					0L, owner, t, sort, set, location, word, corporation, raw));
		});
	}

	private static void flashError() {
		flash("error",
				"Es ist ein Fehler aufgetreten. "
						+ "Bitte versuchen Sie es erneut oder kontaktieren Sie das "
						+ "Entwicklerteam, falls das Problem fortbesteht "
						+ "(siehe Link 'Kontakt' oben rechts).");
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
			final String nwbibspatial, final String nwbibsubject, final int from,
			final int size, String owner, String t, String sort, boolean showDetails,
			String set, String location, String word, String corporation,
			String raw) {
		final WSRequestHolder requestHolder =
				Lobid.request(q, person, name, subject, id, publisher, issued, medium,
						nwbibspatial, nwbibsubject, from, size, owner, t, sort, showDetails,
						set, location, word, corporation, raw);
		return requestHolder.get().map((WSResponse response) -> {
			Long hits = 0L;
			String s = "{}";
			if (response.getStatus() == Http.Status.OK) {
				JsonNode json = response.asJson();
				hits = Lobid.getTotalResults(json);
				s = json.toString();
				if (id.isEmpty()) {
					List<JsonNode> ids = json.findValues("hbzId");
					uncache(
							ids.stream().map(j -> j.asText()).collect(Collectors.toList()));
					Cache.set(session("uuid") + "-lastSearch", ids.toString(), ONE_DAY);
				}
			} else {
				Logger.warn("{}: {} ({}, {})", response.getStatus(),
						response.getStatusText(), requestHolder.getUrl(),
						requestHolder.getQueryParameters());
			}
			return ok(showDetails ? details.render(CONFIG, s, id)
					: search.render(s, q, person, name, subject, id, publisher, issued,
							medium, nwbibspatial, nwbibsubject, from, size, hits, owner, t,
							sort, set, location, word, corporation, raw));
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
	 * @param nwbibspatial Query for the resource nwbibspatial classification
	 * @param nwbibsubject Query for the resource nwbibsubject classification
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
			String nwbibspatial, String nwbibsubject, int from, int size,
			String owner, String t, String field, String sort, String set,
			String location, String word, String corporation, String raw) {

		String key = String.format(
				"facets.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s", field,
				q, person, name, id, publisher, set, location, word, corporation, raw,
				subject, issued, medium, nwbibspatial, nwbibsubject, owner, t);
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
			boolean t1Current = current(subject, medium, nwbibspatial, nwbibsubject,
					owner, t, field, t1);
			boolean t2Current = current(subject, medium, nwbibspatial, nwbibsubject,
					owner, t, field, t2);
			if (t1Current == t2Current) {
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
					? owner : queryParam(owner, term);
			String nwbibsubjectQuery = !field.equals(NWBIB_SUBJECT_FIELD) //
					? nwbibsubject : queryParam(nwbibsubject, term);
			String nwbibspatialQuery = !field.equals(NWBIB_SPATIAL_FIELD) //
					? nwbibspatial : queryParam(nwbibspatial, term);
			String locationQuery = !field.equals(SUBJECT_LOCATION_FIELD) //
					? location : term;
			String subjectQuery = !field.equals(SUBJECT_FIELD) //
					? subject : queryParam(subject, term);
			String issuedQuery = !field.equals(ISSUED_FIELD) //
					? issued : queryParam(issued, term);

			boolean current = current(subject, medium, nwbibspatial, nwbibsubject,
					owner, t, field, term);

			String routeUrl = routes.Application.search(q, person, name, subjectQuery,
					id, publisher, issuedQuery, mediumQuery, nwbibspatialQuery,
					nwbibsubjectQuery, from, size, ownerQuery, typeQuery,
					sort(sort, nwbibspatialQuery, nwbibsubjectQuery, subjectQuery), false,
					set, locationQuery, word, corporation, raw).url();

			String result = String.format(
					"<li " + (current ? "class=\"active\"" : "")
							+ "><a class=\"%s-facet-link\" href='%s'>"
							+ "<input onclick=\"location.href='%s'\" class=\"facet-checkbox\" "
							+ "type=\"checkbox\" %s>&nbsp;%s</input>" + "</a></li>",
					Math.abs(field.hashCode()), routeUrl, routeUrl,
					current ? "checked" : "", fullLabel);

			return result;
		};

		Promise<Result> promise = Lobid.getFacets(q, person, name, subject, id,
				publisher, issued, medium, nwbibspatial, nwbibsubject, owner, field, t,
				set, location, word, corporation, raw).map(json -> {
					Stream<JsonNode> stream =
							StreamSupport.stream(Spliterators.spliteratorUnknownSize(
									json.findValue("entries").elements(), 0), false);
					if (field.equals(ITEM_FIELD)) {
						stream = preprocess(stream);
					}
					String labelKey = String.format(
							"facets-labels.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s",
							field, q, person, name, id, publisher, set, word, corporation,
							raw,
							/* facet values, include in key only if not the current facet: */
							field.equals(SUBJECT_FIELD) ? "" : subject,
							field.equals(ISSUED_FIELD) ? "" : issued,
							field.equals(MEDIUM_FIELD) ? "" : medium,
							field.equals(NWBIB_SPATIAL_FIELD) ? "" : nwbibspatial,
							field.equals(NWBIB_SUBJECT_FIELD) ? "" : nwbibsubject,
							field.equals(ISSUED_FIELD) ? "" : owner,
							field.equals(TYPE_FIELD) ? "" : t,
							field.equals(SUBJECT_LOCATION_FIELD) ? "" : location);

					@SuppressWarnings("unchecked")
					List<Pair<JsonNode, String>> labelledFacets =
							(List<Pair<JsonNode, String>>) Cache.get(labelKey);
					if (labelledFacets == null) {
						labelledFacets = stream.map(toLabel).filter(labelled)
								.collect(Collectors.toList());
						Cache.set(labelKey, labelledFacets, ONE_DAY);
					}

					List<String> list = labelledFacets.stream().sorted(sorter).map(toHtml)
							.collect(Collectors.toList());
					long count = Iterators.size(json.findValue("entries").elements());
					if (count == MAX_FACETS) {
						list.add(0,
								"<li><a href=\"#\">Häufigste (für alle bitte Treffer eingrenzen):</a></li>");
					}
					return list;
				}).map(lis -> ok(String.join("\n", lis)));
		promise.onRedeem(r -> Cache.set(key, r, ONE_DAY));
		return promise;
	}

	private static String sort(String sort, String nwbibspatialQuery,
			String nwbibsubjectQuery, String subjectQuery) {
		return (nwbibspatialQuery + nwbibsubjectQuery + subjectQuery).contains(",")
				? "" /* relevance */ : sort;
	}

	private static boolean current(String subject, String medium,
			String nwbibspatial, String nwbibsubject, String owner, String t,
			String field, String term) {
		return field.equals(MEDIUM_FIELD) && contains(medium, term)
				|| field.equals(TYPE_FIELD) && contains(t, term)
				|| field.equals(ITEM_FIELD) && contains(owner, term)
				|| field.equals(NWBIB_SPATIAL_FIELD) && contains(nwbibspatial, term)
				|| field.equals(NWBIB_SUBJECT_FIELD) && contains(nwbibsubject, term)
				|| field.equals(SUBJECT_FIELD) && contains(subject, term);
	}

	private static boolean contains(String medium, String term) {
		return Arrays.asList(medium.split(",")).contains(term);
	}

	private static String queryParam(String currentParam, String term) {
		if (currentParam.isEmpty())
			return term;
		else if (contains(currentParam, term))
			return currentParam.replace(term, "").replaceAll("\\A,|,\\z", "");
		else
			return currentParam + "," + term;
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
						"term", "http://lobid.org/organisation/" + entry.getKey(), //
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
			uncacheLastSearchUrl();
		}
		return ok("Starred: " + id);
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
		uncacheLastSearchUrl();
		return ok("Unstarred: " + id);
	}

	private static void uncacheLastSearchUrl() {
		String lastSearchUrl = session("lastSearchUrl");
		if (lastSearchUrl != null)
			Cache.remove(session("uuid") + "-" + lastSearchUrl);
	}

	/**
	 * @return A page with all resources starred by the user
	 */
	public static Result showStars() {
		List<String> starredIds = starredIds();
		uncache(starredIds);
		session("lastSearchUrl", routes.Application.showStars().toString());
		Cache.set(session("uuid") + "-lastSearch", starredIds.stream()
				.map(s -> "\"" + s + "\"").collect(Collectors.toList()).toString());
		return ok(stars.render(starredIds));
	}

	/**
	 * @return An OK result to confirm deletion of starred resources
	 */
	public static Result clearStars() {
		session(STARRED, "");
		return ok(stars.render(starredIds()));
	}

	/**
	 * @param path The path to redirect to
	 * @return A 301 MOVED_PERMANENTLY redirect to the path
	 */
	public static Result redirect(String path) {
		return movedPermanently("/" + path);
	}

	private static String currentlyStarred() {
		String starred = session(STARRED);
		return starred == null ? "" : starred.trim();
	}

	private static List<String> starredIds() {
		return new ArrayList<>(Arrays.asList(currentlyStarred().split(" ")).stream()
				.filter(s -> !s.trim().isEmpty()).collect(Collectors.toList()));
	}
}
