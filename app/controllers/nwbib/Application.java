/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.MatchQueryBuilder.Operator;

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

	private static final int ONE_HOUR = 60 * 60;
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
			final int size, final boolean all) {
		String cacheId = String.format("%s.%s.%s.%s.%s", "search", q, from,
				size, all);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		else {
			Logger.debug("Not cached: {}, will cache for one hour", cacheId);
			final Form<String> form = queryForm.bindFromRequest();
			if (form.hasErrors())
				return Promise.promise(() -> badRequest(search.render(CONFIG,
						null, q, from, size, 0L, all)));
			else {
				String query = form.data().get("query");
				Promise<Result> result = okPromise(query != null ? query : q,
						form, from, size, all);
				cacheOnRedeem(cacheId, result, ONE_HOUR);
				return result;
			}
		}
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
			final boolean all) {
		final Promise<Result> result = call(q, form, from, size, all);
		return result.recover((Throwable throwable) -> {
			throwable.printStackTrace();
			flashError();
			return internalServerError(search.render(CONFIG, "[]", q, from,
					size, 0L, all));
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

	private static Promise<Result> call(final String q,
			final Form<String> form, final int from, final int size, boolean all) {
		WSRequestHolder requestHolder = request(q, from, size, all);
		return requestHolder.get().map((WS.Response response) -> {
			JsonNode json = response.asJson();
			Long hits = getTotalResults(json);
			String s = q.isEmpty() ? "[]" : json.toString();
			return ok(search.render(CONFIG, s, q, from, size, hits, all));
		});
	}

	private static Long getTotalResults(JsonNode json) {
		return json.findValue("http://sindice.com/vocab/search#totalResults")
				.asLong();
	}

	private static WSRequestHolder request(final String q, final int from,
			final int size, boolean all) {
		WSRequestHolder requestHolder = WS.url(CONFIG.getString("nwbib.api"))
				.setHeader("Accept", "application/json")
				.setQueryParameter("set", CONFIG.getString("nwbib.set"))
				.setQueryParameter("format", "full")
				.setQueryParameter("from", from + "")
				.setQueryParameter("size", size + "").setQueryParameter("q", q);
		if (!all)
			requestHolder = requestHolder.setQueryParameter("owner", "*");
		Logger.info("Request URL {}, query params {} ", requestHolder.getUrl(),
				requestHolder.getQueryParameters());
		return requestHolder;
	}

	public static Promise<Long> getTotalHits() {
		final Long cachedResult = (Long) Cache.get(String.format("totalHits"));
		if (cachedResult != null) {
			return Promise.promise(() -> {
				return cachedResult;
			});
		}
		WSRequestHolder requestHolder = request("", 0, 0, true);
		return requestHolder.get().map((WS.Response response) -> {
			Long total = getTotalResults(response.asJson());
			Cache.set("totalHits", total, ONE_HOUR);
			return total;
		});
	}
}
