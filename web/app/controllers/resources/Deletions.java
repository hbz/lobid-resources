/* Copyright 2018 by hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;

import com.fasterxml.jackson.databind.JsonNode;

import play.Logger;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * Controller to access the "deletions" index.
 *
 * @author dr0i
 *
 */
public class Deletions extends Controller {

	/**
	 * @param q Query to search in all fields of the deletions index
	 * @param size The maxmimum amount of allowed hits
	 *
	 * @return The search results
	 */
	public static Promise<Result> query(String q, int size) {
		SearchResponse response = Search.elasticsearchClient
				.prepareSearch("deletions").setQuery(QueryBuilders.queryStringQuery(q))
				.setSize(size).execute().actionGet();
		JsonNode resultJson;
		Promise<Result> result = null;
		if (response.getHits().getTotalHits() > 0) {
			StringBuilder source = new StringBuilder(1024);
			source.append(
					"{\"totalHits\": " + response.getHits().totalHits + " , \"hits\":[");
			try {
				response.getHits()
						.forEach(hit -> source.append(hit.getSourceAsString() + ","));
			} catch (Exception e) {
				e.printStackTrace();
			}
			String sourceAsString = source
					.replace(source.length() - 1, source.length(), "]}}").toString();
			resultJson = Json.parse(sourceAsString);
			result = Promise.promise(() -> {
				return Application.responseFor(resultJson,
						Accept.Format.JSON_LD.queryParamString);
			});
		} else {
			Logger.warn("Nothing found when querying" + q);
			result = Promise.promise(() -> {
				return badRequest("Nothing found when querying: '" + q + "'");
			});
		}
		return result;
	}
}
