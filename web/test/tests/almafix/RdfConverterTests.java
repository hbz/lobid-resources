/* Copyright 2017 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package tests.almafix;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.running;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import controllers.resources.RdfConverter;
import controllers.resources.RdfConverter.RdfFormat;
import play.libs.Json;
import play.mvc.Result;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaFunctionalTest
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class RdfConverterTests extends LocalIndexSetup {
	private final URI CONTEXT_URI = new File("conf/context.jsonld").toURI();

	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays
				.asList(new Object[][] { { "990167595410206441" }, { "990110881770206441" } });
	}

	private String id;

	public RdfConverterTests(String id) {
		this.id = id;
	}

	@Test
	public void testJsonldToRdf() {
		running(fakeApplication(), () -> {
			Result result = route(fakeRequest(GET, "/resources/" + id));
			assertThat(result).isNotNull();
			String jsonLd = contentAsString(result);
			assertThat(jsonLd).isNotNull();
			jsonLd = withLocalContext(jsonLd);
			for (RdfFormat format : RdfFormat.values()) {
				String rdf = RdfConverter.toRdf(jsonLd, format);
				assertThat(rdf).isNotNull()
						.as(String.format("RDF in format %s should not be null", format));
			}
		});
	}

	private String withLocalContext(String jsonLd) {
		Map<String, Object> json = Json.fromJson(Json.parse(jsonLd), Map.class);
		json.put("@context", CONTEXT_URI);
		return Json.toJson(json).toString();
	}
}
