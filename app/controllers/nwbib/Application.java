/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.nwbib;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.action.search.SearchResponse;

import play.Logger;
import play.cache.Cache;
import play.cache.Cached;
import play.data.Form;
import play.libs.F.Function;
import play.libs.F.Function0;
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
			return badRequest(index.render(form));
		} else {
			return ok(index.render(form));
		}
	}

	public static Promise<Result> search(final String q, final int from,
			final int size) {
		String cacheId = String.format("%s.%s.%s.%s", "search", q, from, size);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		else {
			Logger.debug("Not cached: {}, will cache for one hour", cacheId);
			final Form<String> form = queryForm.bindFromRequest();
			if (form.hasErrors())
				return badRequestPromise(q, form, from, size);
			else {
				String query = form.data().get("query");
				Promise<Result> result = okPromise(query != null ? query : q,
						form, from, size);
				Cache.set(cacheId, result, ONE_HOUR);
				return result;
			}
		}
	}

	public static Promise<Result> subject(final String q) {
		String cacheId = String.format("%s.%s", "subject", q);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		else {
			Logger.debug("Not cached: {}, will cache for one day", cacheId);
			final String[] callback = request() == null
					|| request().queryString() == null ? null : request()
					.queryString().get("callback");
			Promise<JsonNode> jsonPromise = classificationJsonPromise(q);
			Promise<Result> result;
			if (callback != null)
				result = jsonPromise.map(okSubject(callback[0]));
			else
				result = jsonPromise.map(okSubject());
			Cache.set(cacheId, result, ONE_DAY);
			return result;
		}
	}

	public static Result register(final String t) {
		Result cachedResult = (Result) Cache.get("register." + t);
		if (cachedResult != null)
			return cachedResult;
		SearchResponse response = CLASSIFICATION.dataFor(t);
		if (response == null)
			return badRequest("Unsupported register type: " + t);
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
		if (response == null)
			return badRequest("Unsupported classification type: " + t);
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

	private static Promise<Result> badRequestPromise(final String q,
			final Form<String> form, final int from, final int size) {
		return Promise.promise(new Function0<Result>() {
			public Result apply() {
				return badRequest(search.render(CONFIG, form, null, q, from,
						size));
			}
		});
	}

	private static Promise<Result> okPromise(final String q,
			final Form<String> form, final int from, final int size) {
		final Promise<Result> result = call(q, form, from, size);
		return result.recover(new Function<Throwable, Result>() {
			@Override
			public Result apply(Throwable throwable) throws Throwable {
				throwable.printStackTrace();
				return ok(search.render(CONFIG, form, "[]", q, from, size));
			}
		});
	}

	private static Promise<JsonNode> classificationJsonPromise(final String q) {
		return Promise.promise(new Function0<JsonNode>() {
			public JsonNode apply() {
				return CLASSIFICATION.ids(q);
			}
		});
	}

	private static Function<JsonNode, Result> okSubject(final String callback) {
		return new Function<JsonNode, Result>() {
			public Result apply(JsonNode json) {
				return ok(String.format("%s(%s)", callback,
						Json.stringify(json)));
			}
		};
	}

	private static Function<JsonNode, Result> okSubject() {
		return new Function<JsonNode, Result>() {
			public Result apply(JsonNode json) {
				return ok(json);
			}
		};
	}

	private static Promise<Result> call(final String q,
			final Form<String> form, final int from, final int size) {
		final WSRequestHolder requestHolder = WS
				.url(CONFIG.getString("nwbib.api"))
				.setHeader("Accept", "application/json")
				.setQueryParameter("set", CONFIG.getString("nwbib.set"))
				.setQueryParameter("format", "full")
				.setQueryParameter("from", from + "")
				.setQueryParameter("size", size + "")
				.setQueryParameter("q", preprocess(q));
		Logger.info("Request URL {}, query params {} ", requestHolder.getUrl(),
				requestHolder.getQueryParameters());
		return requestHolder.get().map(new Function<WS.Response, Result>() {
			public Result apply(WS.Response response) {
				String s = q.isEmpty() ? "[]" : response.asJson().toString();
				return ok(search.render(CONFIG, form, s, q, from, size));
			}
		});
	}

	private static String preprocess(String query) {
		/* Workaround for https://github.com/hbz/nwbib/issues/4 */
		return query.replaceAll("0\\b", "");
	}
}
