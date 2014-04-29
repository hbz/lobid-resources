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

import play.libs.F.Callback;
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
				HTMLUNIT, new Callback<TestBrowser>() {
					public void invoke(TestBrowser browser) {
						browser.goTo("http://localhost:3333/nwbib/classification?t=Raumsystematik");
						assertThat(browser.pageSource())
								.contains("Nordrhein-Westfalen")
								.contains("Rheinland")
								.contains("Grafschaft, Herzogtum Jülich");
					}
				});
	}

	@Test
	public void testClassification() {
		running(testServer(3333, fakeApplication(inMemoryDatabase())),
				HTMLUNIT, new Callback<TestBrowser>() {
					public void invoke(TestBrowser browser) {
						browser.goTo("http://localhost:3333/nwbib/classification?t=Sachsystematik");
						assertThat(browser.pageSource())
								.contains("Allgemeine Landeskunde")
								.contains("Landesbeschreibungen")
								.contains("Reiseberichte");
					}
				});
	}

}
