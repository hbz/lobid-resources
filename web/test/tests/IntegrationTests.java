/* Copyright 2014-2017 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static play.test.Helpers.GET;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;

import controllers.resources.Queries;
import controllers.resources.Search;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaFunctionalTest
 */
@SuppressWarnings("javadoc")
public class IntegrationTests extends LocalIndexSetup {

	@Before
	public void setUp() throws Exception {
		Map<String, String> flashData = Collections.emptyMap();
		Map<String, Object> argData = Collections.emptyMap();
		play.api.mvc.RequestHeader header = mock(play.api.mvc.RequestHeader.class);
		Http.Request request = mock(Http.Request.class);
		Http.Context context =
				new Http.Context(2L, header, request, flashData, flashData, argData);
		Http.Context.current.set(context);
	}

	@Test
	public void testFacets() {
		running(testServer(3333), () -> {
			Search index =
					new Search.Builder().query(new Queries.Builder().q("köln").build())
							.aggs(Joiner.on(",").join(Search.SUPPORTED_AGGREGATIONS)).build();
			Search queryResources = index.queryResources();
			Aggregations facets = queryResources.getAggregations();
			Terms terms = facets.get("type");
			Stream<String> values =
					terms.getBuckets().stream().map(Bucket::getKeyAsString);
			Stream<Long> counts =
					terms.getBuckets().stream().map(Bucket::getDocCount);
			assertThat(values.collect(Collectors.toList())).contains(
					"BibliographicResource", "Book", "Thesis", "Miscellaneous",
					"Article");
			assertThat(counts.collect(Collectors.toList())).excludes(0);
		});
	}

	@Test
	public void renderTemplate() {
		running(testServer(3333), () -> {
			Result result =
					route(fakeRequest(GET, "/resources/search?q=buch&format=html"));
			assertThat(result).isNotNull();
			assertThat(result.contentType()).isEqualTo("text/html");
			String text = Helpers.contentAsString(result);
			assertThat(text).contains("lobid-resources").contains("buch");
		});
	}

	@Test
	public void bulkRequest() {
		bulkRequestWith("jsonl");
	}

	@Test
	// old format param for bulk, keep for comaptibility, see:
	// https://github.com/hbz/lobid-resources/issues/861
	public void bulkRequestCompatibility() {
		bulkRequestWith("bulk");
	}

	private static void bulkRequestWith(String param) {
		running(testServer(3333), () -> {
			Result result =
					route(fakeRequest(GET, "/resources/search?q=buch&format=" + param));
			assertThat(result).isNotNull();
			assertThat(result.contentType()).isEqualTo("application/x-jsonlines");
			String text = Helpers.contentAsString(result);
			assertThat(text.split("\\n").length).isGreaterThanOrEqualTo(10);
			assertThat(result.header(Http.HeaderNames.CONTENT_DISPOSITION))
					.isNotNull().isNotEmpty().contains("attachment; filename=");
		});
	}

	@Test
	public void sizeRequest() {
		running(testServer(3333), () -> {
			Search index = new Search.Builder()
					.query(new Queries.Builder().q("hbzId:TT050409948").build()).build();
			Long hits = index.totalHits();
			assertThat(hits).isGreaterThan(0);
		});
	}

	@Test
	public void agentRequest() {
		running(testServer(3333), () -> {
			for (String s : Arrays.asList("Westfalen",
					"http://d-nb.info/gnd/5265186-1", //
					"Reulecke, Jürgen (1940-)", //
					"Reiff, Johann J. (1793-1864)", //
					"http://d-nb.info/gnd/5265186-1,http://d-nb.info/gnd/5265186-1,AND")) {
				assertThat(new Search.Builder()
						.query(new Queries.Builder().agent(s).build()).build().totalHits())
								.as(s).isGreaterThanOrEqualTo(1);
			}
		});
	}

	@Test
	public void responseJsonFilterGet() {
		running(testServer(3333), () -> {
			Search index = new Search.Builder().build();
			JsonNode hit = index.getResource("TT050409948").getResult();
			assertThat(hit.isObject()).as("hit is an object").isTrue();
			assertThat(hit.findValue("hbzId").asText()).isEqualTo("TT050409948");
			Search.HIDE_FIELDS.forEach(field -> assertThat(hit.get(field)).isNull());
		});
	}

	@Test
	public void responseJsonFilterSearch() {
		running(testServer(3333), () -> {
			Search index = new Search.Builder()
					.query(new Queries.Builder().q("*").build()).size(100).build();
			index = index.queryResources();
			assertThat(index.getTotal()).isGreaterThanOrEqualTo(100);
			JsonNode hits = index.getResult();
			assertThat(hits.isArray()).as("hits is an array").isTrue();
			List<JsonNode> nodes = new ArrayList<>();
			Iterator<JsonNode> elements = hits.elements();
			elements.forEachRemaining(node -> {
				Search.HIDE_FIELDS
						.forEach(field -> assertThat(node.get(field)).as(field).isNull());
				nodes.add(node);
			});
			assertThat(nodes.size()).isGreaterThanOrEqualTo(100);
			assertThat(new HashSet<>(nodes).size()).as("unique hits")
					.isEqualTo(nodes.size());
		});
	}

	@Test
	public void queryFilter() {
		running(testServer(3333), () -> {
			Search all =
					new Search.Builder().query(new Queries.Builder().q("*").build())
							.size(100).build().queryResources();
			Search nwbib = new Search.Builder()
					.query(new Queries.Builder().q("*")
							.filter("inCollection.id:HT014176012").build())
					.size(100).build().queryResources();
			assertThat(all.getTotal()).isGreaterThan(nwbib.getTotal());
		});
	}

	@Test
	public void contextContentTypeAndCorsHeaderContext() {
		testJsonld("/resources/context.jsonld");
	}

	@Test
	public void contextContentTypeAndCorsHeaderDataset() {
		testJsonld("/resources/dataset.jsonld");
	}

	private static void testJsonld(String path) {
		running(fakeApplication(), () -> {
			Result result = route(fakeRequest(GET, path));
			assertThat(result).isNotNull();
			assertThat(result.contentType()).isEqualTo("application/ld+json");
			assertThat(result.header("Access-Control-Allow-Origin")).isEqualTo("*");
		});
	}

	@Test
	public void jsonRequestNoInternalUrl() {
		running(fakeApplication(), () -> {
			Result result =
					route(fakeRequest(GET, "/resources/search?q=*&format=json"));
			assertThat(result).isNotNull();
			assertThat(result.contentType()).isEqualTo("application/json");
			JsonNode json = Json.parse(Helpers.contentAsString(result));
			assertThat(json.get("id").toString()).contains("lobid.org");
			assertThat(json.get("@context").toString()).contains("lobid.org");
		});
	}

}
