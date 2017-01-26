/* Copyright 2014-2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.header;
import static play.test.Helpers.route;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.resources.Application;
import controllers.resources.Index;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import play.twirl.api.Content;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaFunctionalTest
 */
@SuppressWarnings("javadoc")
public class InternalIntegrationTest {

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
			String queryString =
					Application.buildQueryString("köln", "", "", "", "", "", "", "", "");
			Index queryResources = new Index().queryResources(queryString, 0, 0, "");
			JsonNode facets = queryResources.getAggregations();
			assertThat(facets.get("type").findValues("key").stream()
					.map(e -> e.asText()).collect(Collectors.toList())).contains(
							"bibliographicresource", "article", "book", "periodical",
							"multivolumebook", "thesis", "miscellaneous", "proceedings",
							"editedvolume", "biography", "festschrift", "newspaper",
							"bibliography", "series", "officialpublication",
							"referencesource", "publishedscore", "legislation", "image",
							"game");
			assertThat(facets.findValues("doc_count").stream().map(e -> e.intValue())
					.collect(Collectors.toList())).excludes(0);
		});
	}

	@Test
	public void renderTemplate() {
		String query = "buch";
		int from = 0;
		int size = 10;
		running(testServer(3333), () -> {
			Content html = views.html.query.render("[]", query, "", "", "", "", "",
					"", "", from, size, 0L, "", "", "", "");
			assertThat(Helpers.contentType(html)).isEqualTo("text/html");
			String text = Helpers.contentAsString(html);
			assertThat(text).contains("lobid-resources").contains("buch");
		});
	}

	@Test
	public void sizeRequest() {
		running(testServer(3333), () -> {
			Index index = new Index();
			Long hits =
					index.queryResources("hbzId:HT018486420", 0, 10, "").getTotal();
			assertThat(hits).isGreaterThan(0);
			hits = index.queryResources("hbzId:HT002091108", 0, 10, "").getTotal();
			assertThat(hits).isGreaterThan(0);
			hits = index.queryResources("hbzId:HT001387709", 0, 10, "").getTotal();
			assertThat(hits).isGreaterThan(0);
		});
	}

	@Test
	public void contextContentTypeAndCorsHeader() {
		running(fakeApplication(), () -> {
			Result result = route(fakeRequest(GET, "/resources/context.jsonld"));
			assertThat(result).isNotNull();
			assertThat(contentType(result)).isEqualTo("application/ld+json");
			assertThat(header("Access-Control-Allow-Origin", result)).isEqualTo("*");
		});
	}

}
