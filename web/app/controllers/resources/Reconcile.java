/* Copyright 2017-2023 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.SearchHit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.Logger;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

/**
 * OpenRefine reconciliation service controller.
 * 
 * Serves reconciliation service meta data and multi query requests.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Reconcile extends Controller {

	private static final JsonNode TYPES =
			Json.toJson(Arrays.asList("BibliographicResource"));

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
			.ofPattern("dd/MMM/yyyy:HH:mm:ss Z").withZone(ZoneId.systemDefault());

	/**
	 * @param callback The name of the JSONP function to wrap the response in
	 * @param queries The queries. If this and extend are empty, return service
	 *          metadata
	 * @param extend The extension data. If this and queries are empty, return
	 *          service metadata
	 * @return OpenRefine reconciliation results (if queries is not empty), data
	 *         extension information (if extend is not empty), or endpoint meta
	 *         data (if queries and extend are empty), wrapped in `callback`
	 */
	public static Result main(String callback, String queries, String extend) {
		ObjectNode result = queries.isEmpty() && extend.isEmpty() ? metadata()
				: (!queries.isEmpty() ? queries(queries) : null);
		response().setHeader("Access-Control-Allow-Origin", "*");
		final String resultString = prettyJsonString(result);
		return (callback.isEmpty() ? ok(resultString)
				: ok(String.format("/**/%s(%s);", callback, resultString)))
						.as("application/json; charset=utf-8");
	}

	private static ObjectNode metadata() {
		final String host = Application.CONFIG.getString("host");
		ObjectNode result = Json.newObject();
		result.putArray("versions").add("0.1").add("0.2");
		result.put("name",
				"lobid-resources reconciliation for OpenRefine (" + host + ")");
		result.put("identifierSpace", host + "/resources");
		result.put("schemaSpace", "http://purl.org/dc/terms/BibliographicResource");
		result.set("defaultTypes", TYPES);
		result.set("view", Json.newObject().put("url", host + "/resources/{{id}}"));
		result.set("preview", Json.newObject()//
				.put("height", 300)//
				.put("width", 600)//
				.put("url", host + "/resources/{{id}}.preview"));
		return result;
	}

	private static String prettyJsonString(JsonNode jsonNode) {
		try {
			return new ObjectMapper().writerWithDefaultPrettyPrinter()
					.writeValueAsString(jsonNode);
		} catch (JsonProcessingException x) {
			x.printStackTrace();
			return null;
		}
	}

	/** @return Reconciliation data for the queries in the request */
	public static Result reconcile() {
		Map<String, String[]> body = request().body().asFormUrlEncoded();
		response().setHeader("Access-Control-Allow-Origin", "*");
		Result result = body.containsKey("extend") ? Results.TODO
				: ok(queries(body.get("queries")[0]));
		// Apache-compatible POST logging, see
		// https://github.com/hbz/lobid-gnd/issues/207#issuecomment-526571646
		Logger.info("{} {} - [{}] \"{} {}\" {}",
				request().headers().getOrDefault("X-Forwarded-For",
						new String[] { request().remoteAddress() }),
				request().host(), TIME_FORMATTER.format(Instant.now()),
				request().method(), request().path(), result.status());
		return result;
	}

	private static ObjectNode queries(String src) {
		JsonNode request = Json.parse(src);
		Iterator<Entry<String, JsonNode>> inputQueries = request.fields();
		ObjectNode response = Json.newObject();
		while (inputQueries.hasNext()) {
			Entry<String, JsonNode> inputQuery = inputQueries.next();
			Logger.info("q: " + inputQuery);
			Search searchResponse = executeQuery(inputQuery,
					preprocess(mainQuery(inputQuery)), propQuery(inputQuery));
			List<ObjectNode> results =
					mapToResults(mainQuery(inputQuery), searchResponse);
			ObjectNode resultsForInputQuery = Json.newObject();
			resultsForInputQuery.set("result", Json.toJson(results));
			Logger.info("r: " + resultsForInputQuery);
			response.set(inputQuery.getKey(), resultsForInputQuery);
		}
		return response;
	}

	private static List<ObjectNode> mapToResults(String mainQuery,
			Search searchHits) {
		List<ObjectNode> result = new ArrayList<>();
		searchHits.getResult().elements().forEachRemaining(hit -> {
			Map<String, Object> map = new ObjectMapper().convertValue(hit,
					new TypeReference<Map<String, Object>>() {/**/
					});
			ObjectNode resultForHit = Json.newObject();
			String[] elements = hit.get("id").asText().split("/");
			resultForHit.put("id", elements[elements.length - 1].replace("#!", ""));
			Object nameObject = map.get("title");
			String name = nameObject == null ? "" : nameObject + "";
			resultForHit.put("name", name);
			// TODO: temp, need a proper score solution, with query, see
			// https://github.com/hbz/lobid-resources/issues/635
			resultForHit.set("score", hit.get("_score"));
			resultForHit.put("match", false);
			resultForHit.set("type", hit.get("type"));
			result.add(resultForHit);
		});
		markMatch(result);
		return result;
	}

	private static void markMatch(List<ObjectNode> result) {
		if (!result.isEmpty()) {
			ObjectNode topResult = result.get(0);
			int bestScore = topResult.get("score").asInt();
			if (bestScore > 50 && (result.size() == 1
					|| bestScore - result.get(1).get("score").asInt() > 10)) {
				topResult.put("match", true);
			}
		}
	}

	private static Search executeQuery(Entry<String, JsonNode> entry,
			String queryString, String propString) {
		JsonNode limitNode = entry.getValue().get("limit");
		int limit = limitNode == null ? -1 : limitNode.asInt();
		JsonNode typeNode = entry.getValue().get("type");
		String filter = typeNode == null ? "" : "type:" + typeNode.asText();
		QueryStringQueryBuilder mainQuery =
				QueryBuilders.queryStringQuery(queryString)//
						.field("title", 8f)//
						.field("alternativeTitle", 4f)//
						.field("otherTitleInformation", 2f)//
						.field("responsibilityStatement")//
						.field("rpbId", 8f)//
						.field("hbzId", 8f)//
						.field("almaMmsId", 8f)//
						.field("sameAs.id", 2f)//
						.field("id", 8f);//

		BoolQueryBuilder query = QueryBuilders.boolQuery().must(mainQuery)
				// TODO: temp, don't reconcile against RPB records:
				.mustNot(queryStringQuery("_exists_:rpbId"));
		if (!filter.isEmpty()) {
			query = query.should(queryStringQuery(filter).boost(8f));
		}
		if (propString != null && !propString.trim().isEmpty()) {
			query = query.should(queryStringQuery(propString).boost(5f));
		}
		return new Search.Builder().query(query).from(0).size(limit).build()
				.queryResources((SearchHit hit) -> {
					Map<String, Object> source = hit.getSource();
					// TODO: temp, need a proper score solution, with query, see
					// https://github.com/hbz/lobid-resources/issues/635
					source.put("_score", hit.getScore());
					return Json.toJson(source);
				});
	}

	private static QueryStringQueryBuilder queryStringQuery(String q) {
		return QueryBuilders.queryStringQuery(q).defaultOperator(Operator.AND);
	}

	private static String propQuery(Entry<String, JsonNode> entry) {
		List<String> segments = new ArrayList<>();
		JsonNode props = entry.getValue().get("properties");
		if (props != null) {
			Logger.debug("Properties: {}", props);
			for (JsonNode p : props) {
				String field = p.get("pid").asText();
				String value = preprocess(p.get("v").asText().trim());
				if (!value.isEmpty()) {
					segments.add("(" + field + ":" + value + ")");
				}
			}
		}
		String queryString = segments.stream().collect(Collectors.joining(" OR "));
		Logger.debug("Property query string: {}", queryString);
		return queryString;
	}

	static String preprocess(String s) {
		return s.startsWith("http") || isGndId(s) ? "\"" + s + "\""
				: /* index.validate(s) ? s : */ clean(s); // TODO add validation
	}

	private static boolean isGndId(String string) {
		return string.matches(
				// https://www.wikidata.org/wiki/Property:P227#P1793
				"1[012]?\\d{7}[0-9X]|[47]\\d{6}-\\d|[1-9]\\d{0,7}-[0-9X]|3\\d{7}[0-9X]");
	}

	private static String clean(String in) {
		String out = in.replaceAll("[\"!/:+\\-=<>(){}\\[\\]^]", " ");
		if (!in.equals(out)) {
			Logger.info("Cleaned query string '{}' to: '{}'", in, out);
		}
		return out;
	}

	private static String mainQuery(Entry<String, JsonNode> entry) {
		return entry.getValue().get("query").asText();
	}
}
