/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.nwbib.Application;
import controllers.nwbib.Lobid;
import play.libs.F.Promise;
import play.mvc.Http;
import play.test.Helpers;
import play.twirl.api.Content;
import views.ReverseGeoLookup;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaFunctionalTest
 */
@SuppressWarnings("javadoc")
public class IntegrationTest {

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
			String field = Application.TYPE_FIELD;
			Promise<JsonNode> jsonPromise = Lobid.getFacets("köln", "", "", "", "",
					"", "", "", "", "", "", field, "", "", "", "", "", "");
			JsonNode facets = jsonPromise.get(Lobid.API_TIMEOUT);
			assertThat(facets.findValues("term").stream().map(e -> e.asText())
					.collect(Collectors.toList())).contains(
							"http://purl.org/dc/terms/BibliographicResource",
							"http://purl.org/ontology/bibo/Article",
							"http://purl.org/ontology/bibo/Book",
							"http://purl.org/ontology/bibo/Journal",
							"http://purl.org/ontology/bibo/MultiVolumeBook",
							"http://purl.org/ontology/bibo/Thesis",
							"http://purl.org/lobid/lv#Miscellaneous",
							"http://purl.org/ontology/bibo/Proceedings",
							"http://purl.org/lobid/lv#EditedVolume",
							"http://purl.org/lobid/lv#Biography",
							"http://purl.org/lobid/lv#Festschrift",
							"http://purl.org/ontology/bibo/Newspaper",
							"http://purl.org/lobid/lv#Bibliography",
							"http://purl.org/ontology/bibo/Series",
							"http://purl.org/lobid/lv#OfficialPublication",
							"http://purl.org/ontology/bibo/ReferenceSource",
							"http://purl.org/ontology/mo/PublishedScore",
							"http://purl.org/lobid/lv#Legislation",
							"http://purl.org/ontology/bibo/Image",
							"http://purl.org/library/Game");
			assertThat(facets.findValues("count").stream().map(e -> e.intValue())
					.collect(Collectors.toList())).excludes(0);
		});
	}

	@Test
	public void renderTemplate() {
		String query = "buch";
		int from = 0;
		int size = 10;
		running(testServer(3333), () -> {
			Content html = views.html.search.render("[{}]", query, "", "", "", "", "",
					"", "", "", "", from, size, 0L, "", "", "", "", "", "", "", "");
			assertThat(Helpers.contentType(html)).isEqualTo("text/html");
			String text = Helpers.contentAsString(html);
			assertThat(text).contains("NWBib").contains("buch")
					.contains("Sachsystematik").contains("Raumsystematik");
		});
	}

	@Test
	public void sizeRequest() {
		running(testServer(3333), () -> {
			Long hits = Lobid
					.getTotalHits("@graph.http://purl.org/lobid/lv#multiVolumeWork.@id",
							"http://lobid.org/resource/HT018486420", "")
					.get(Lobid.API_TIMEOUT);
			assertThat(hits).isGreaterThan(0);
			hits = Lobid
					.getTotalHits("@graph.http://purl.org/lobid/lv#series.@id",
							"http://lobid.org/resource/HT002091108", "")
					.get(Lobid.API_TIMEOUT);
			assertThat(hits).isGreaterThan(0);
			hits = Lobid
					.getTotalHits("@graph.http://purl.org/lobid/lv#containedIn.@id",
							"http://lobid.org/resource/HT001387709", "")
					.get(Lobid.API_TIMEOUT);
			assertThat(hits).isGreaterThan(0);
		});
	}

	@Test
	public void reverseGeoLookup() {
		running(testServer(3333), () -> {
			assertEquals("Menden (Sauerland)",
					ReverseGeoLookup.of("51.433333391323686,7.800000105053186"));
		});
	}

}
