/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.HTMLUNIT;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.inMemoryDatabase;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import org.junit.Ignore;
import org.junit.Test;

import controllers.nwbib.Application;
import play.data.Form;
import play.mvc.Content;
import play.test.Helpers;
import play.test.TestBrowser;

@Ignore
/* Uses actual data, not available in CI. Run locally with `play test`. */
/**
 * See http://www.playframework.com/documentation/2.2.x/JavaFunctionalTest
 */
public class IntegrationTest {

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
	public void renderTemplate() {
		String query = "buch";
		int from = 0;
		int size = 10;
		running(testServer(3333, fakeApplication(inMemoryDatabase())),
				HTMLUNIT,
				(TestBrowser browser) -> {
					Content html = views.html.search.render(Application.CONFIG,
							Form.form(String.class).fill(query), "[]", query,
							from, size, 0L, true);
					assertThat(Helpers.contentType(html))
							.isEqualTo("text/html");
					String text = Helpers.contentAsString(html);
					assertThat(text).contains("NWBib").contains("buch")
							.contains("Sachsystematik")
							.contains("Raumsystematik");
				});
	}

}
