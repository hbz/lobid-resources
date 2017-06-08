/* Copyright 2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.running;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import controllers.resources.RdfConverter;
import controllers.resources.RdfConverter.RdfFormat;
import play.mvc.Result;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaFunctionalTest
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class RdfConverterTests extends LocalIndexSetup {

	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(RdfFormat.values()).stream()
				.map(format -> new Object[] { format }).collect(Collectors.toList());
	}

	private RdfFormat format;

	public RdfConverterTests(RdfFormat format) {
		this.format = format;
	}

	@Test
	public void testJsonldToRdf() {
		running(fakeApplication(), () -> {
			Result result = route(fakeRequest(GET, "/resources/TT050409948"));
			assertThat(result).isNotNull();
			String jsonLd = contentAsString(result);
			assertThat(jsonLd).isNotNull();
			String rdf = RdfConverter.toRdf(jsonLd, format);
			assertThat(rdf).isNotNull();
		});
	}
}
