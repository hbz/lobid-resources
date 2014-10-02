/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.elasticsearch.action.search.SearchResponse;

import play.Logger;
import play.Play;
import play.api.cache.EhCachePlugin;
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
import views.html.details;
import views.html.index;
import views.html.search;
import views.html.stars;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * The main application controller.
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class Application extends Controller {

	private static final String STARRED = "starred";

	/** The internal ES field for the type facet. */
	public static final String TYPE_FIELD = "@graph.@type";
	/** The internal ES field for the medium facet. */
	public static final String MEDIUM_FIELD =
			"@graph.http://purl.org/dc/terms/medium.@id";
	/** The internal ES field for the item/exemplar facet. */
	public static final String ITEM_FIELD =
			"@graph.http://purl.org/vocab/frbr/core#exemplar.@id";

	private static final File FILE = new File("conf/nwbib.conf");
	/** Access to the nwbib.conf config file. */
	public final static Config CONFIG = ConfigFactory.parseFile(
			FILE.exists() ? FILE : new File("modules/nwbib/conf/nwbib.conf"))
			.resolve();

	static Form<String> queryForm = Form.form(String.class);

	/** Access to the NWBib classification data stored in ES. */
	public final static Classification CLASSIFICATION = new Classification(
			CONFIG.getString("nwbib.cluster"), CONFIG.getString("nwbib.server"));

	static final int ONE_HOUR = 60 * 60;
	private static final int ONE_DAY = 24 * ONE_HOUR;

	/** @return The NWBib index page. */
	@Cached(key = "nwbib.index", duration = ONE_HOUR)
	public static Result index() {
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors())
			return badRequest(index.render());
		return ok(index.render());
	}

	/** @return The NWBib advanced search page. */
	@Cached(key = "nwbib.advanced", duration = ONE_HOUR)
	public static Result advanced() {
		return ok(views.html.advanced.render());
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
	 * @param from The page start (offset of page of resource to return)
	 * @param size The page size (size of page of resource to return)
	 * @param owner Owner filter for resource queries
	 * @param t Type filter for resource queries
	 * @param sort Sorting order for results ("newest", "oldest", "" -> relevance)
	 * @param details If true, render details
	 * @return The search results
	 */
	public static Promise<Result> search(final String q, final String author,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final String nwbibspatial, final String nwbibsubject, final int from,
			final int size, final String owner, String t, String sort, boolean details) {
		String cacheId =
				String.format("%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s",
						"search", q, author, name, subject, id, publisher, issued, medium,
						nwbibspatial, nwbibsubject, from, size, owner, t, sort);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		Logger.debug("Not cached: {}, will cache for one hour", cacheId);
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors())
			return Promise.promise(() -> badRequest(search.render(CONFIG, null, q,
					author, name, subject, id, publisher, issued, medium, nwbibspatial,
					nwbibsubject, from, size, 0L, owner, t, sort)));
		String query = form.data().get("query");
		Promise<Result> result =
				okPromise(query != null ? query : q, author, name, subject, id,
						publisher, issued, medium, nwbibspatial, nwbibsubject, from, size,
						owner, t, sort, details);
		cacheOnRedeem(cacheId, result, ONE_HOUR);
		return result;
	}

	/**
	 * @param id The resource ID.
	 * @return The details page for the resource with the given ID.
	 */
	public static Promise<Result> show(final String id) {
		return search("", "", "", "", id, "", "", "", "", "", 0, 1, "", "", "",
				true);
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
			result =
					jsonPromise.map((JsonNode json) -> ok(String.format("%s(%s)",
							callback, Json.stringify(json))));
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
		SearchResponse response = CLASSIFICATION.dataFor(t);
		if (response == null) {
			Logger.error("Failed to get data for register type: " + t);
			flashError();
			return internalServerError(browse_register.render(null, t));
		}
		JsonNode sorted = CLASSIFICATION.sorted(response);
		Result result = ok(browse_register.render(sorted.toString(), t));
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
		SearchResponse response = CLASSIFICATION.dataFor(t);
		if (response == null) {
			Logger.error("Failed to get data for classification type: " + t);
			flashError();
			return internalServerError(browse_classification.render(null, null, t));
		}
		Result result = classificationResult(response, t);
		Cache.set("classification." + t, result, ONE_DAY);
		return result;
	}

	private static Result classificationResult(SearchResponse response, String t) {
		List<JsonNode> topClasses = new ArrayList<>();
		Map<String, List<JsonNode>> subClasses = new HashMap<>();
		CLASSIFICATION.buildHierarchy(response, topClasses, subClasses);
		String topClassesJson = Json.toJson(topClasses).toString();
		return ok(browse_classification.render(topClassesJson, subClasses, t));
	}

	private static Promise<Result> okPromise(final String q, final String author,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final String nwbibspatial, final String nwbibsubject, final int from,
			final int size, final String owner, String t, String sort, boolean details) {
		final Promise<Result> result =
				call(q, author, name, subject, id, publisher, issued, medium,
						nwbibspatial, nwbibsubject, from, size, owner, t, sort, details);
		return result.recover((Throwable throwable) -> {
			throwable.printStackTrace();
			flashError();
			return internalServerError(search.render(CONFIG, "[]", q, author, name,
					subject, id, publisher, issued, medium, nwbibspatial, nwbibsubject,
					from, size, 0L, owner, t, sort));
		});
	}

	private static void flashError() {
		flash("error", "Es ist ein Fehler aufgetreten. "
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

	static Promise<Result> call(final String q, final String author,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final String nwbibspatial, final String nwbibsubject, final int from,
			final int size, String owner, String t, String sort, boolean showDetails) {
		WSRequestHolder requestHolder =
				Lobid.request(q, author, name, subject, id, publisher, issued, medium,
						nwbibspatial, nwbibsubject, from, size, owner, t, sort);
		return requestHolder.get().map(
				(WSResponse response) -> {
					JsonNode json = response.asJson();
					Long hits = Lobid.getTotalResults(json);
					String s =
							q.isEmpty() && author.isEmpty() && name.isEmpty()
									&& subject.isEmpty() && id.isEmpty() && publisher.isEmpty()
									&& issued.isEmpty() && medium.isEmpty()
									&& nwbibspatial.isEmpty() && nwbibsubject.isEmpty() ? "[]"
									: json.toString();
					return ok(showDetails ? details.render(CONFIG, s, q) : search.render(
							CONFIG, s, q, author, name, subject, id, publisher, issued,
							medium, nwbibspatial, nwbibsubject, from, size, hits, owner, t,
							sort));
				});
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
	 * @param from The page start (offset of page of resource to return)
	 * @param size The page size (size of page of resource to return)
	 * @param owner Owner filter for resource queries
	 * @param t Type filter for resource queries
	 * @param field The facet field (the field to facet over)
	 * @param sort Sorting order for results ("newest", "oldest", "" -> relevance)
	 * @return The search results
	 */
	public static Promise<Result> facets(String q, String author, String name,
			String subject, String id, String publisher, String issued,
			String medium, String nwbibspatial, String nwbibsubject, int from,
			int size, String owner, String t, String field, String sort) {
		String key =
				String.format("facets.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s", q,
						author, name, subject, id, publisher, issued, medium, nwbibspatial,
						nwbibsubject, owner, field, sort, t);
		Result cachedResult = (Result) Cache.get(key);
		if (cachedResult != null) {
			return Promise.promise(() -> cachedResult);
		}
		Predicate<JsonNode> labelled =
				json -> {
					String term = json.get("term").asText();
					String typeLabel = Lobid.facetLabel(Arrays.asList(term), field);
					String typeIcon = Lobid.facetIcon(Arrays.asList(term), field);
					return !typeLabel.startsWith("http") && !typeLabel.isEmpty()
							&& !typeIcon.isEmpty();
				};
		Function<JsonNode, String> toHtml =
				json -> {
					String term = json.get("term").asText();
					int count = json.get("count").asInt();
					String icon = Lobid.facetIcon(Arrays.asList(term), field);
					String label = Lobid.facetLabel(Arrays.asList(term), field);
					String mediumQuery = !field.equals(MEDIUM_FIELD) ? medium : term;
					String typeQuery = !field.equals(TYPE_FIELD) ? t : term;
					String ownerQuery = !field.equals(ITEM_FIELD) ? owner : term;
					String routeUrl =
							routes.Application.search(q, author, name, subject, id,
									publisher, issued, mediumQuery, nwbibspatial, nwbibsubject,
									from, size, ownerQuery, typeQuery, sort, false).url();
					String result =
							String.format(
									"<li><a href='%s'><span class='%s'/>&nbsp;%s (%s)</a></li>",
									routeUrl, icon, label, count);
					return result;
				};
		Comparator<? super JsonNode> sorter = (j1, j2) -> {
			String t1 = j1.get("term").asText();
			String t2 = j2.get("term").asText();
			String l1 = Lobid.facetLabel(Arrays.asList(t1), field);
			String l2 = Lobid.facetLabel(Arrays.asList(t2), field);
			return l1.compareTo(l2);
		};
		Promise<Result> promise =
				Lobid
						.getFacets(q, author, name, subject, id, publisher, issued, medium,
								nwbibspatial, nwbibsubject, owner, field, t)
						.map(
								json -> {
									Stream<JsonNode> stream =
											StreamSupport.stream(
													Spliterators.spliteratorUnknownSize(
															json.findValue("entries").elements(), 0), false);
									if (field.equals(ITEM_FIELD)) {
										stream = preprocess(stream);
									}
									return stream.sorted(sorter).filter(labelled).map(toHtml)
											.collect(Collectors.toList());
								}).map(lis -> ok(String.join("\n", lis)));
		promise.onRedeem(r -> Cache.set(key, r, ONE_DAY));
		return promise;
	}

	private static Stream<JsonNode> preprocess(Stream<JsonNode> stream) {
		String captureItemUriWithoutSignature =
				"(http://lobid\\.org/item/[^:]*?:[^:]*?:)[^\"]*";
		List<String> itemUrisWithoutSignatures =
				stream
						.map(
								json -> json.get("term").asText()
										.replaceAll(captureItemUriWithoutSignature, "$1"))
						.distinct().collect(Collectors.toList());
		return count(itemUrisWithoutSignatures).entrySet().stream()
				.map(entry -> Json.toJson(ImmutableMap.of(//
						"term", "http://lobid.org/organisation/" + entry.getKey(),//
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
		session(STARRED, currentlyStarred() + " " + id);
		uncache(id);
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
		uncache(id);
		return ok("Unstarred: " + id);
	}

	/** @return A page with all resources starred by the user */
	public static Result showStars() {
		return ok(stars.render(starredIds()));
	}

	/** @return An OK result to confirm deletion of starred resources */
	public static Result clearStars() {
		session(STARRED, "");
		return ok(stars.render(starredIds()));
	}

	private static void uncache(String id) {
		try {
			Play.application().plugin(EhCachePlugin.class).manager().getCache("play")
					.removeAll();
		} catch (Throwable t) {
			Logger.error("Could not clear cache", t);
			Cache.remove(String.format("%s.%s.%s.%s.%s.%s", "search", id, 0, 1, "",
					"", true));
		}
	}

	private static String currentlyStarred() {
		String starred = session(STARRED);
		return starred == null ? "" : starred.trim();
	}

	private static List<String> starredIds() {
		return new ArrayList<>(Arrays.asList(currentlyStarred().split(" "))
				.stream().filter(s -> !s.trim().isEmpty()).collect(Collectors.toList()));
	}
}
