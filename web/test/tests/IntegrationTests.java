/* Copyright 2014-2019 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

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
import play.api.Logger;
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
            assertThat(values.collect(Collectors.toList())).contains("BibliographicResource", "Book", "Bibliography",
                "EditedVolume", "Game", "Image", "Periodical", "Series");
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
					route(fakeRequest(GET, "/resources/search?q=theorie&format=" + param));
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
					.query(new Queries.Builder().q("hbzId:HT020202475").build()).build();
			Long hits = index.totalHits();
			assertThat(hits).isGreaterThan(0);
		});
	}

	@Test
	public void sizeRequestOwnerFull() {
		ownerTest("http://lobid.org/organisations/DE-290#!");
	}

	@Test
	public void sizeRequestOwnerAbout() {
		ownerTest("http://lobid.org/organisations/DE-290");
	}

	@Test
	public void sizeRequestOwnerShort() {
		ownerTest("DE-290");
	}

	@Test
	public void sizeRequestOwnerShortMulti() {
		ownerTest("DE-5,DE-290");
	}

	private static void ownerTest(String id) {
		running(testServer(3333), () -> {
			Search index = new Search.Builder()
					.query(new Queries.Builder().owner(id).build()).build();
			assertThat(index.totalHits()).isGreaterThan(0);
		});
	}

	@Test
	public void sizeRequestIdAlmaMmsId() {
		idTest("990053976760206441");
	}

	@Test
	public void sizeRequestIdZdbId() {
		idTest("123550-3");
	}

	@Test
	public void sizeRequestIdIsbn10() {
		idTest("0-40503-920-4");
		idTest("0405039204");
	}

	@Test
	public void sizeRequestIdIsbn13() {
		idTest("978-0-40503-920-1");
		idTest("9780405039201");
	}

	private static void idTest(String id) {
		running(testServer(3333), () -> {
			Search index = new Search.Builder()
					.query(new Queries.Builder().id(id).build()).build();
			assertThat(index.totalHits()).isGreaterThan(0);
		});
	}

	@Test
	public void agentRequest() {
		running(testServer(3333), () -> {
			for (String s : Arrays.asList("Westfalen",
                    "120195364", //
					"Breuer, Stefan (1948-)", //
					"https://d-nb.info/gnd/120195364,AND")) {
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
			JsonNode hit = index.getResource("990363946050206441").getResult();
			assertThat(hit.isObject()).as("hit is an object").isTrue();
			assertThat(hit.findValue("hbzId").asText()).isEqualTo("HT020202475");
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
							.filter("inCollection.id:ZDB-197-MSE").build())
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

	@Test
	public void jsonResponseRetainFieldOrder() {
		running(fakeApplication(), () -> {
			Result result = route(fakeRequest(GET,
					"/resources/search?q=*&size=1&format=json"));
			JsonNode json = Json.parse(Helpers.contentAsString(result));
			assertThat(json.get("member").toString())
					.matches(".*\"@context\"[^,]+?,\"id\"[^,]+?,\"type\".*");
		});
	}

}
