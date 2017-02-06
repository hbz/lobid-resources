/* Copyright 2014-2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.resources;

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
import java.util.Map.Entry;
import java.util.Spliterators;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.gdata.util.common.base.PercentEscaper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import play.Logger;
import play.Play;
import play.cache.Cache;
import play.cache.Cached;
import play.data.Form;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import views.html.api;
import views.html.details;
import views.html.details_item;
import views.html.index;
import views.html.query;
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
	public static final String TYPE_FIELD = "type";
	/** The internal ES field for the medium facet. */
	public static final String MEDIUM_FIELD = "medium.id";
	/** The internal ES field for the item/exemplar facet. */
	public static final String ITEM_FIELD = "exemplar.id";
	/** The internal ES field for subjects. */
	public static final String SUBJECT_FIELD = "subject.id";
	/** The internal ES field for issued years. */
	public static final String ISSUED_FIELD = "publication.startDate";
	/** Access to the resources.conf config file. */
	public final static Config CONFIG =
			ConfigFactory.parseFile(new File("conf/resources.conf")).resolve();

	static Form<String> queryForm = Form.form(String.class);

	static final int ONE_HOUR = 60 * 60;
	/** The number of seconds in one day. */
	public static final int ONE_DAY = 24 * ONE_HOUR;

	/**
	 * @return The index page.
	 */
	public static Result index() {
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors())
			return badRequest(index.render());
		return ok(index.render());
	}

	/**
	 * @return The API documentation page
	 */
	@Cached(duration = ONE_HOUR, key = "api")
	public static Result api() {
		return ok(api.render());
	}

	/**
	 * @return The advanced search page.
	 */
	@Cached(key = "search.advanced", duration = ONE_HOUR)
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
	 * @param agent Query for a agent associated with the resource
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
	 * @param set The set
	 * @param format The response format ('html' or 'json')
	 * @return The search results
	 */
	public static Promise<Result> query(final String q, final String agent,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final int from, final int size, final String owner, String t, String sort,
			String set, String format) {
		addCorsHeader();
		String uuid = session("uuid");
		if (uuid == null)
			session("uuid", UUID.randomUUID().toString());
		String cacheId = String.format("%s-%s-%s", uuid, request().uri(),
				Accept.formatFor(format, request().acceptedTypes()));
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		Logger.debug("Not cached: {}, will cache for one hour", cacheId);
		Promise<Result> result = Promise.promise(() -> {
			Index index = new Index();
			String queryString = index.buildQueryString(q, agent, name, subject, id,
					publisher, issued, medium, t, set);
			Index queryResources =
					index.queryResources(queryString, from, size, sort, owner);
			JsonNode json = queryResources.getResult();
			String s = json.toString();
			String responseFormat =
					Accept.formatFor(format, request().acceptedTypes());
			boolean htmlRequested =
					responseFormat.equals(Accept.Format.HTML.queryParamString);
			return htmlRequested ? ok(query.render(s, q, agent, name, subject, id,
					publisher, issued, medium, from, size, queryResources.getTotal(),
					owner, t, sort, set)) : prettyJsonOk(json);
		});
		cacheOnRedeem(cacheId, result, ONE_HOUR);
		return result.recover((Throwable throwable) -> {
			Logger.error("Could not query index", throwable);
			flashError();
			return internalServerError(query.render("[]", q, agent, name, subject, id,
					publisher, issued, medium, from, size, 0L, owner, t, sort, set));
		});
	}

	@SuppressWarnings({ "javadoc", "unused" }) // WIP
	public static Promise<Result> aggregations(final String q, final String agent,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final int from, final int size, final String owner, String t, String sort,
			String set, String format) {
		Index index = new Index();
		String queryString = index.buildQueryString(q, agent, name, subject, id,
				publisher, issued, medium, t, set);
		Callable<Map<String, List<Map<String, Object>>>> getAggregations = () -> {
			Logger.debug("Not cached: aggregations {}, will cache for one hour",
					queryString);
			Index queryResources =
					index.queryResources(queryString, from, size, sort, owner);
			Map<String, List<Map<String, Object>>> aggregations = new HashMap<>();
			for (final Entry<String, Aggregation> aggregation : queryResources
					.getAggregations().asMap().entrySet()) {
				Terms terms = (Terms) aggregation.getValue();
				Stream<Map<String, Object>> buckets =
						terms.getBuckets().stream().map((Bucket b) -> ImmutableMap.of(//
								"key", b.getKeyAsString(), "doc_count", b.getDocCount()));
				aggregations.put(aggregation.getKey(),
						buckets.collect(Collectors.toList()));
			}
			Cache.set(queryString, aggregations);
			return aggregations;
		};
		return Promise.promise(() -> ok(
				Json.toJson(Cache.getOrElse(queryString, getAggregations, ONE_HOUR))));
	}

	/**
	 * @param id The resource ID.
	 * @param format The response format ('html' or 'json')
	 * @return The details page for the resource with the given ID.
	 */
	public static Promise<Result> show(final String id, String format) {
		addCorsHeader();
		return Promise.promise(() -> {
			JsonNode result = new Index().getResource(id).getResult();
			String responseFormat =
					Accept.formatFor(format, request().acceptedTypes());
			boolean htmlRequested =
					responseFormat.equals(Accept.Format.HTML.queryParamString);
			return htmlRequested ? ok(details.render(CONFIG, result.toString(), id))
					: prettyJsonOk(result);
		});
	}

	/**
	 * @param id The item ID.
	 * @param format The response format ('html' or 'json')
	 * @return The details for the item with the given ID.
	 */
	public static Promise<Result> item(final String id, String format) {
		String responseFormat = Accept.formatFor(format, request().acceptedTypes());
		return Promise.promise(() -> {
			/* @formatter:off
			 * Escape item IDs for index lookup the same way as during transformation, see:
			 * https://github.com/hbz/lobid-resources/blob/master/src/main/resources/morph-hbz01-to-lobid.xml#L781
			 * https://github.com/hbz/lobid-resources/blob/master/src/main/java/org/lobid/resources/UrlEscaper.java#L31
			 * @formatter:on
			 */
			JsonNode itemJson = new Index().getItem(
					new PercentEscaper(PercentEscaper.SAFEPATHCHARS_URLENCODER, false)
							.escape(id))
					.getResult();
			if (responseFormat.equals("html")) {
				return itemJson == null ? notFound(details_item.render(id, ""))
						: ok(details_item.render(id, itemJson.toString()));
			}
			return itemJson == null ? notFound("Not found: " + id)
					: prettyJsonOk(itemJson);

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

	private static Status prettyJsonOk(JsonNode responseJson)
			throws JsonProcessingException {
		return ok(new ObjectMapper().writerWithDefaultPrettyPrinter()
				.writeValueAsString(responseJson))
						.as("application/json; charset=utf-8");
	}

	private static void addCorsHeader() {
		response().setHeader("Access-Control-Allow-Origin", "*");
	}

	private static void uncache(List<String> ids) {
		for (String id : ids) {
			Cache.remove(String.format("%s-/resources/%s", session("uuid"), id));
		}
	}

	/**
	 * @param q Query to search in all fields
	 * @param agent Query for a agent associated with the resource
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
	 * @param set The set
	 * @return The search results
	 */
	public static Promise<Result> facets(String q, String agent, String name,
			String subject, String id, String publisher, String issued, String medium,
			int from, int size, String owner, String t, String field, String sort,
			String set) {

		String key =
				String.format("facets.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s", field, q,
						agent, name, id, publisher, set, subject, issued, medium, owner, t);
		Result cachedResult = (Result) Cache.get(key);
		if (cachedResult != null) {
			return Promise.promise(() -> cachedResult);
		}

		String labelTemplate = "<span class='%s'/>&nbsp;%s (%s)";

		Function<JsonNode, Pair<JsonNode, String>> toLabel = json -> {
			String term = json.get("key").asText();
			int count = json.get("doc_count").asInt();
			String icon = Lobid.facetIcon(Arrays.asList(term), field);
			String label = Lobid.facetLabel(Arrays.asList(term), field, "");
			String fullLabel = String.format(labelTemplate, icon, label, count);
			return Pair.of(json, fullLabel);
		};

		Predicate<Pair<JsonNode, String>> labelled = pair -> {
			JsonNode json = pair.getLeft();
			String label = pair.getRight();
			int count = json.get("doc_count").asInt();
			return (!label.contains("http") || label.contains("nwbib")) && label
					.length() > String.format(labelTemplate, "", "", count).length();
		};

		Collator collator = Collator.getInstance(Locale.GERMAN);
		Comparator<Pair<JsonNode, String>> sorter = (p1, p2) -> {
			String t1 = p1.getLeft().get("key").asText();
			String t2 = p2.getLeft().get("key").asText();
			boolean t1Current = current(subject, medium, owner, t, field, t1);
			boolean t2Current = current(subject, medium, owner, t, field, t2);
			if (t1Current == t2Current) {
				if (!field.equals(ISSUED_FIELD)) {
					Integer c1 = p1.getLeft().get("doc_count").asInt();
					Integer c2 = p2.getLeft().get("doc_count").asInt();
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
			String term = json.get("key").asText();
			String mediumQuery = !field.equals(MEDIUM_FIELD) //
					? medium : queryParam(medium, term);
			String typeQuery = !field.equals(TYPE_FIELD) //
					? t : queryParam(t, term);
			String ownerQuery = !field.equals(ITEM_FIELD) //
					? owner : withoutAndOperator(queryParam(owner, term));
			String subjectQuery = !field.equals(SUBJECT_FIELD) //
					? subject : queryParam(subject, term);
			String issuedQuery = !field.equals(ISSUED_FIELD) //
					? issued : queryParam(issued, term);

			boolean current = current(subject, medium, owner, t, field, term);

			String routeUrl = routes.Application.query(q, agent, name, subjectQuery,
					id, publisher, issuedQuery, mediumQuery, from, size, ownerQuery,
					typeQuery, sort(sort, subjectQuery), set, null).url();

			String result = String.format(
					"<li " + (current ? "class=\"active\"" : "")
							+ "><a class=\"%s-facet-link\" href='%s'>"
							+ "<input onclick=\"location.href='%s'\" class=\"facet-checkbox\" "
							+ "type=\"checkbox\" %s>&nbsp;%s</input>" + "</a></li>",
					Math.abs(field.hashCode()), routeUrl, routeUrl,
					current ? "checked" : "", fullLabel);

			return result;
		};

		Promise<Result> promise =
				aggregations(q, agent, name, subject, id, publisher, issued, medium,
						from, size, owner, t, sort, set, "json").map(result -> {
							JsonNode json = Json.parse(Helpers.contentAsString(result));
							Stream<JsonNode> stream =
									StreamSupport.stream(Spliterators.spliteratorUnknownSize(
											json.get(field).elements(), 0), false);
							if (field.equals(ITEM_FIELD)) {
								stream = preprocess(stream);
							}
							String labelKey = String.format(
									"facets-labels.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s", field, q,
									agent, name, id, publisher, set, subject, issued, medium,
									field.equals(ITEM_FIELD) ? "" : owner, t);

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
			String t, String field, String term) {
		return field.equals(MEDIUM_FIELD) && contains(medium, term)
				|| field.equals(TYPE_FIELD) && contains(t, term)
				|| field.equals(ITEM_FIELD) && contains(owner, term)
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

	private static Stream<JsonNode> preprocess(Stream<JsonNode> stream) {
		String captureItemUriWithoutSignature =
				"(http://lobid\\.org/items/[^:]*?:[^:]*?:)[^\"]*";
		List<String> itemUrisWithoutSignatures = stream
				.map(json -> json.get("key").asText()
						.replaceAll(captureItemUriWithoutSignature, "$1"))
				.distinct().collect(Collectors.toList());
		return count(itemUrisWithoutSignatures).entrySet().stream()
				.map(entry -> Json.toJson(ImmutableMap.of(//
						"key", Lobid.ORGS_BETA_ROOT + entry.getKey(), //
						"doc_count", entry.getValue())));
	}

	private static Map<String, Integer> count(List<String> itemUris) {
		String captureIsilOrgIdentifier =
				"http://lobid\\.org/items/[^:]*?:([^:]*?):[^\"]*";
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

	/**
	 * @return JSON-LD context
	 */
	public static Result context() {
		response().setContentType("application/ld+json");
		addCorsHeader();
		return ok(Play.application().resourceAsStream("context.jsonld"));
	}
}
