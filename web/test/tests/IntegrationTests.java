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
import java.util.stream.Stream;

import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.junit.Before;
import org.junit.Test;

import controllers.resources.Index;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import play.twirl.api.Content;

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
			Index index = new Index();
			String queryString =
					index.buildQueryString("köln", "", "", "", "", "", "", "", "");
			Index queryResources = index.queryResources(queryString);
			Aggregations facets = queryResources.getAggregations();
			Terms terms = facets.get("type");
			Stream<String> values =
					terms.getBuckets().stream().map(Bucket::getKeyAsString);
			Stream<Long> counts =
					terms.getBuckets().stream().map(Bucket::getDocCount);
			assertThat(values.collect(Collectors.toList())).contains(
					"bibliographicresource", "book", "thesis", "miscellaneous",
					"article");
			assertThat(counts.collect(Collectors.toList())).excludes(0);
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
			Long hits = index.queryResources("hbzId:TT050409948").getTotal();
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
