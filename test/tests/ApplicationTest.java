/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;

import org.junit.Test;

import play.data.Form;
import play.mvc.Content;
import controllers.nwbib.Application;

/**
 * See http://www.playframework.com/documentation/2.2.x/JavaTest
 */
public class ApplicationTest {

	@Test
	public void shortClassificationId() {
		assertThat(Application.shortId("http://purl.org/lobid/nwbib#s58206"))
				.as("short classification").isEqualTo("s58206");
	}

	@Test
	public void shortSpatialClassificationId() {
		assertThat(
				Application.shortId("http://purl.org/lobid/nwbib-spatial#n58"))
				.as("short spatial classification").isEqualTo("n58");
	}

	@Test
	public void renderTemplate() {
		String query = "buch";
		int from = 0;
		int size = 10;
		Content html = views.html.search.render(Application.CONFIG,
				Form.form(String.class).fill(query), "[]", query, from, size);
		assertThat(contentType(html)).isEqualTo("text/html");
		String text = contentAsString(html);
		assertThat(text).contains("NWBib").contains("buch")
				.contains("Sachsystematik").contains("Raumsystematik");
	}
}
