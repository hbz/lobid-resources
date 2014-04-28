/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;

import org.junit.Test;

import play.mvc.Result;
import play.test.Helpers;

/**
 * 
 * Simple (JUnit) tests that can call all parts of a play app. If you are
 * interested in mocking a whole application, see the wiki for more details.
 * 
 */
public class ApplicationTest {

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void renderTemplate() {
		Result result = Helpers
				.callAction(controllers.nwbib.routes.ref.Application.search(
						"buch", 0, 10));
		assertThat(contentType(result)).isEqualTo("text/html");
		String text = contentAsString(result);
		assertThat(text).contains("Buch");
	}
}
