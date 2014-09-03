/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static play.test.Helpers.HTMLUNIT;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.inMemoryDatabase;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import play.libs.F.Promise;
import play.mvc.Content;
import play.mvc.Http;
import play.test.Helpers;
import play.test.TestBrowser;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.nwbib.Application;
import controllers.nwbib.Lobid;

/* Uses actual data, not available in CI. Run locally with `play test`. */
/**
 * See http://www.playframework.com/documentation/2.2.x/JavaFunctionalTest
 */
public class IntegrationTest {

	@Before
	public void setUp() throws Exception {
		Map<String, String> flashData = Collections.emptyMap();
		Map<String, Object> argData = Collections.emptyMap();
		play.api.mvc.RequestHeader header = mock(play.api.mvc.RequestHeader.class);
		Http.Request request = mock(Http.Request.class);
		Http.Context context = new Http.Context(2L, header, request, flashData,
				flashData, argData);
		Http.Context.current.set(context);
	}

	@Test
	public void testSpatialClassification() {
		running(testServer(3333, fakeApplication(inMemoryDatabase())),
				HTMLUNIT,
				(TestBrowser browser) -> {
					browser.goTo("http://localhost:3333/nwbib/classification?t=Raumsystematik");
					assertThat(browser.pageSource())
							.contains("Nordrhein-Westfalen")
							.contains("Rheinland")
							.contains("Grafschaft, Herzogtum Jülich");
				});
	}

	@Test
	public void testClassification() {
		running(testServer(3333, fakeApplication(inMemoryDatabase())),
				HTMLUNIT,
				(TestBrowser browser) -> {
					browser.goTo("http://localhost:3333/nwbib/classification?t=Sachsystematik");
					assertThat(browser.pageSource())
							.contains("Allgemeine Landeskunde")
							.contains("Landesbeschreibungen")
							.contains("Reiseberichte");
				});
	}

	@Test
	public void testFacets() {
		String field = Application.TYPE_FIELD;
		Promise<JsonNode> jsonPromise = Lobid.getFacets("köln", "", "", "", "", "", "", "", "all", field, "");
		JsonNode facets = jsonPromise.get(10000);
		assertThat(
				facets.findValues("term").stream().map(e -> e.asText())
						.collect(Collectors.toList())).contains(
				"http://purl.org/dc/terms/BibliographicResource",
				"http://purl.org/vocab/frbr/core#Manifestation",
				"http://purl.org/ontology/bibo/Document",
				"http://purl.org/ontology/bibo/Article",
				"http://purl.org/ontology/bibo/Book",
				"http://purl.org/ontology/bibo/Collection",
				"http://iflastandards.info/ns/isbd/terms/mediatype/T1002",
				"http://purl.org/ontology/bibo/Journal",
				"http://purl.org/ontology/bibo/Periodical",
				"http://purl.org/ontology/bibo/MultiVolumeBook");
		assertThat(
				facets.findValues("count").stream().map(e -> e.intValue())
						.collect(Collectors.toList())).excludes(0);
	}

	@Test
	public void renderTemplate() {
		String query = "buch";
		int from = 0;
		int size = 10;
		running(testServer(3333, fakeApplication(inMemoryDatabase())),
				HTMLUNIT,
				(TestBrowser browser) -> {
					Content html = views.html.search.render(Application.CONFIG,
							"[{}]", query, "", "", "", "", "", "", "", from, size, 0L, "", "", "");
					assertThat(Helpers.contentType(html))
							.isEqualTo("text/html");
					String text = Helpers.contentAsString(html);
					assertThat(text).contains("NWBib").contains("buch")
							.contains("Sachsystematik")
							.contains("Raumsystematik");
				});
	}

}
