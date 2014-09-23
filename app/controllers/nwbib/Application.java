/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
import play.libs.WS;
import play.libs.WS.WSRequestHolder;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.browse_classification;
import views.html.browse_register;
import views.html.index;
import views.html.search;
import views.html.details;
import views.html.stars;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Application extends Controller {

	private static final String STARRED = "starred";

	public static final String TYPE_FIELD = "@graph.@type";
	public static final String MEDIUM_FIELD = "@graph.http://purl.org/dc/terms/medium.@id";

	static Form<String> queryForm = Form.form(String.class);

	private static final File FILE = new File("conf/nwbib.conf");
	public final static Config CONFIG = ConfigFactory.parseFile(
			FILE.exists() ? FILE : new File("modules/nwbib/conf/nwbib.conf"))
			.resolve();

	public final static Classification CLASSIFICATION = new Classification(
			CONFIG.getString("nwbib.cluster"), CONFIG.getString("nwbib.server"));

	static final int ONE_HOUR = 60 * 60;
	private static final int ONE_DAY = 24 * ONE_HOUR;

	@Cached(key = "nwbib.index", duration = ONE_HOUR)
	public static Result index() {
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(index.render());
		} else {
			return ok(index.render());
		}
	}

	@Cached(key = "nwbib.advanced", duration = ONE_HOUR)
	public static Result advanced() {
		return ok(views.html.advanced.render());
	}

	public static Promise<Result> search(final String q, final String author,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final String nwbibspatial, final String nwbibsubject, 
			final int from, final int size, final String ownerParam, String t,
			String sort, boolean details) {
		final String owner = ownerParam(ownerParam);
		String cacheId = String.format(
				"%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s", "search", q,
				author, name, subject, id, publisher, issued, medium,
				nwbibspatial, nwbibsubject, from, size, owner, t, sort);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		else {
			Logger.debug("Not cached: {}, will cache for one hour", cacheId);
			final Form<String> form = queryForm.bindFromRequest();
			if (form.hasErrors())
				return Promise.promise(() -> badRequest(search.render(CONFIG,
						null, q, author, name, subject, id, publisher, issued,
						medium, nwbibspatial, nwbibsubject, from, size, 0L,
						owner, t, sort)));
			else {
				String query = form.data().get("query");
				Promise<Result> result = okPromise(query != null ? query : q,
						author, name, subject, id, publisher, issued, medium,
						nwbibspatial, nwbibsubject, form, from, size, owner, t,
						sort, details);
				cacheOnRedeem(cacheId, result, ONE_HOUR);
				return result;
			}
		}
	}

	static String ownerParam(final String requestParam) {
		if (!requestParam.isEmpty()) {
			session("owner", requestParam);
			return requestParam;
		} else {
			String sessionParam = session("owner");
			return sessionParam != null ? sessionParam : "all";
		}
	}

	public static Promise<Result> show(final String id) {
		return search("", "", "", "", id, "", "", "", "", "", 0, 1, "all", "", "", true);
	}

	public static Promise<Result> subject(final String q, final String callback, final String t) {
		String cacheId = String.format("%s.%s.%s.%s", "subject", q, callback, t);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		else {
			Logger.debug("Not cached: {}, will cache for one day", cacheId);
			Promise<JsonNode> jsonPromise = Promise
					.promise(() -> CLASSIFICATION.ids(q, t));
			Promise<Result> result;
			if (!callback.isEmpty())
				result = jsonPromise.map((JsonNode json) -> ok(String.format(
						"%s(%s)", callback, Json.stringify(json))));
			else
				result = jsonPromise.map((JsonNode json) -> ok(json));
			cacheOnRedeem(cacheId, result, ONE_DAY);
			return result;
		}
	}

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
		List<JsonNode> topClasses = new ArrayList<JsonNode>();
		Map<String, List<JsonNode>> subClasses = new HashMap<>();
		CLASSIFICATION.buildHierarchy(response, topClasses, subClasses);
		String topClassesJson = Json.toJson(topClasses).toString();
		return ok(browse_classification.render(topClassesJson, subClasses, t));
	}

	private static Promise<Result> okPromise(final String q,
			final String author, final String name, final String subject,
			final String id, final String publisher, final String issued,
			final String medium, final String nwbibspatial,
			final String nwbibsubject, final Form<String> form, final int from,
			final int size, final String owner, String t, String sort,
			boolean details) {
		final Promise<Result> result = call(q, author, name, subject, id,
				publisher, issued, medium, nwbibspatial, nwbibsubject, form,
				from, size, owner, t, sort, details);
		return result.recover((Throwable throwable) -> {
			throwable.printStackTrace();
			flashError();
			return internalServerError(search.render(CONFIG, "[]", q, author,
					name, subject, id, publisher, issued, medium, nwbibspatial,
					nwbibsubject, from, size, 0L, owner, t, sort));
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
			if (play.test.Helpers.status(result) == play.test.Helpers.OK)
				Cache.set(cacheId, resultPromise, duration);
		});
	}

	static Promise<Result> call(final String q, final String author,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final String nwbibspatial, final String nwbibsubject,
			final Form<String> form, final int from, final int size,
			String owner, String t, String sort, boolean showDetails) {
		WSRequestHolder requestHolder = Lobid.request(q, author, name, subject,
				id, publisher, issued, medium, nwbibspatial, nwbibsubject,
				from, size, owner, t, sort);
		return requestHolder.get().map(
				(WS.Response response) -> {
					JsonNode json = response.asJson();
					Long hits = Lobid.getTotalResults(json);
					String s = q.isEmpty() && author.isEmpty()
							&& name.isEmpty() && subject.isEmpty()
							&& id.isEmpty() && publisher.isEmpty()
							&& issued.isEmpty() && medium.isEmpty()
							&& nwbibspatial.isEmpty() && nwbibsubject.isEmpty() ? "[]"
							: json.toString();
					return ok(showDetails ? details.render(CONFIG, s, q)
							: search.render(CONFIG, s, q, author, name,
									subject, id, publisher, issued, medium,
									nwbibspatial, nwbibsubject, from, size,
									hits, owner, t, sort));
				});
	}

	public static Promise<Result> facets(String q, String author, String name,
			String subject, String id, String publisher, String issued,
			String medium, String nwbibspatial, String nwbibsubject, int from,
			int size, String owner, String t, String field, String sort) {
		String key = String.format(
				"facets.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s", q, author,
				name, subject, id, publisher, issued, medium, nwbibspatial,
				nwbibsubject, owner, field, sort, t);
		Result cachedResult = (Result) Cache.get(key);
		if(cachedResult!=null){
			return Promise.promise(() -> cachedResult);
		}
		Predicate<JsonNode> labelled = json -> {
			String term = json.get("term").asText();
			String typeLabel = Lobid.facetLabel(Arrays.asList(term), field);
			String typeIcon = Lobid.facetIcon(Arrays.asList(term), field);
			return !typeLabel.isEmpty() && !typeIcon.isEmpty();
		};
		Function<JsonNode, String> toHtml = json -> {
			String term = json.get("term").asText();
			int count = json.get("count").asInt();
			String icon = Lobid.facetIcon(Arrays.asList(term), field);
			// TODO we need a general solution for this when we add more facets
			String routeUrl = routes.Application.search(q, author, name,
					subject, id, publisher, issued,
					field.equals(TYPE_FIELD) ? medium : term, nwbibspatial, nwbibsubject, from, size, owner,
					field.equals(TYPE_FIELD) ? term : t, sort, false).url();
			return String
					.format("<li><a href='%s'><span class='%s'/>&nbsp;%s (%s)</a></li>",
							routeUrl, icon, Lobid.facetLabel(Arrays.asList(term), field), count);
		};
		Promise<Result> promise = Lobid
				.getFacets(q, author, name, subject, id, publisher, issued,
						medium, nwbibspatial, nwbibsubject, owner, field, t)
				.map(json -> StreamSupport.stream(
						Spliterators.spliteratorUnknownSize(json.findValue("entries").elements(), 0), false)
						.filter(labelled)
						.map(toHtml)
						.collect(Collectors.toList()))
				.map(lis -> ok(String.join("\n", lis)));
		promise.onRedeem(r -> Cache.set(key, r, ONE_DAY));
		return promise;
	}

	public static boolean isStarred(String id){
		return starredIds().contains(id);
	}

	public static Result star(String id){
		session(STARRED, currentlyStarred() + " " + id);
		uncache(id);
		return ok("Starred: " + id);
	}

	public static Result unstar(String id){
		List<String> starred = starredIds();
		starred.remove(id);
		session(STARRED, String.join(" ", starred));
		uncache(id);
		return ok("Unstarred: " + id);
	}

	public static Result showStars(){
		return ok(stars.render(starredIds()));
	}

	public static Result clearStars(){
		session(STARRED, "");
		return ok(stars.render(starredIds()));
	}

	private static void uncache(String id) {
		try {
			Play.application().plugin(EhCachePlugin.class).manager()
					.getCache("play").removeAll();
		} catch (Throwable t) {
			Logger.error("Could not clear cache", t);
			Cache.remove(String.format("%s.%s.%s.%s.%s.%s", "search", id, 0, 1,
					ownerParam(""), "", true));
		}
	}

	private static String currentlyStarred() {
		String starred = session(STARRED);
		return starred == null ? "" : starred.trim();
	}

	private static List<String> starredIds() {
		return new ArrayList<>(Arrays.asList(
				currentlyStarred().split(" ")).stream()
				.filter(s->!s.trim().isEmpty()).collect(Collectors.toList()));
	}
}
