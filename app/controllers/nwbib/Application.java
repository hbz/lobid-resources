/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.facet.terms.TermsFacet;

import play.Logger;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Application extends Controller {

	static Form<String> queryForm = Form.form(String.class);

	private static final File FILE = new File("conf/nwbib.conf");
	public final static Config CONFIG = ConfigFactory.parseFile(
			FILE.exists() ? FILE : new File("modules/nwbib/conf/nwbib.conf"))
			.resolve();

	final static Classification CLASSIFICATION = new Classification(
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

	public static Promise<Result> search(final String q, final int from,
			final int size, final String ownerParam, String t, boolean details) {
		final String owner = ownerParam(ownerParam);
		String cacheId = String.format("%s.%s.%s.%s.%s.%s", "search", q, from,
				size, owner, t);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		else {
			Logger.debug("Not cached: {}, will cache for one hour", cacheId);
			final Form<String> form = queryForm.bindFromRequest();
			if (form.hasErrors())
				return Promise.promise(() -> badRequest(search.render(CONFIG,
						null, q, from, size, 0L, owner, t)));
			else {
				String query = form.data().get("query");
				Promise<Result> result = okPromise(query != null ? query : q,
						form, from, size, owner, t, details);
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
		return search(id, 0, 1, "", "", true);
	}

	public static Promise<Result> subject(final String q, final String callback) {
		String cacheId = String.format("%s.%s.%s", "subject", q, callback);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		else {
			Logger.debug("Not cached: {}, will cache for one day", cacheId);
			Promise<JsonNode> jsonPromise = Promise
					.promise(() -> CLASSIFICATION.ids(q));
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
			return internalServerError(browse_register.render(null));
		}
		JsonNode sorted = CLASSIFICATION.sorted(response);
		Result result = ok(browse_register.render(sorted.toString()));
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
			return internalServerError(browse_classification.render(null, null));
		}
		Result result = classificationResult(response);
		Cache.set("classification." + t, result, ONE_DAY);
		return result;
	}

	private static Result classificationResult(SearchResponse response) {
		List<JsonNode> topClasses = new ArrayList<JsonNode>();
		Map<String, List<JsonNode>> subClasses = new HashMap<>();
		CLASSIFICATION.buildHierarchy(response, topClasses, subClasses);
		String topClassesJson = Json.toJson(topClasses).toString();
		return ok(browse_classification.render(topClassesJson, subClasses));
	}

	private static Promise<Result> okPromise(final String q,
			final Form<String> form, final int from, final int size,
			final String owner, String t, boolean details) {
		final Promise<Result> result = call(q, form, from, size, owner, t, details);
		return result.recover((Throwable throwable) -> {
			throwable.printStackTrace();
			flashError();
			return internalServerError(search.render(CONFIG, "[]", q, from,
					size, 0L, owner, t));
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

	static Promise<Result> call(final String q, final Form<String> form,
			final int from, final int size, String owner, String t,
			boolean showDetails) {
		WSRequestHolder requestHolder = Lobid.request(q, from, size, owner, t);
		return requestHolder.get().map(
				(WS.Response response) -> {
					JsonNode json = response.asJson();
					Long hits = Lobid.getTotalResults(json);
					String s = q.isEmpty() ? "[]" : json.toString();
					return ok(showDetails ? details.render(CONFIG, s, q) : search
							.render(CONFIG, s, q, from, size, hits, owner, t));
				});
	}

	public static Promise<Result> facets(String q, int from, int size, String owner, String t, String field){
		String key = String.format("facets.%s.%s.%s",q,owner,field);
		Result cachedResult = (Result) Cache.get(key);
		if(cachedResult!=null){
			return Promise.promise(() -> cachedResult);
		}
		Promise<Result> promise = Lobid.getFacets(q, owner, field)
			.map(fs->((TermsFacet)(fs.facet(field))).getEntries())
			.map(es->es.stream().filter(e -> {
				String typeLabel = Lobid.typeLabel(Arrays.asList(e.getTerm().toString()));
				String typeIcon = Lobid.typeIcon(Arrays.asList(e.getTerm().toString()));
				return !typeLabel.isEmpty() && !typeIcon.isEmpty();
			})
			.map(e->{
				String term = e.getTerm().toString();
				String icon = Lobid.typeIcon(Arrays.asList(e.getTerm().toString()));
				String routeUrl = routes.Application.search(q,from,size,owner,term,false).absoluteURL(request());
				return String.format(
						"<li><a href='%s'><span class='%s'/>&nbsp;%s (%s)</a></li>",
						routeUrl,icon,Lobid.typeLabel(Arrays.asList(term)),e.getCount()
				);
			})
			.collect(Collectors.toList()))
			.map(lis -> ok(String.join("\n", lis)));
		promise.onRedeem(r -> Cache.set(key, r, ONE_DAY));
		return promise;
	}

}
