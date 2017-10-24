/* Copyright 2014-2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.fakeRequest;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import controllers.resources.Accept;
import play.api.http.MediaRange;
import play.mvc.Http;

/**
 * Unit tests for functionality provided by the {@link Accept} class.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class AcceptUnitTest {

	// test data parameters, formatted as "input /*->*/ expected output"
	@Parameters
	public static Collection<Object[]> data() {
		// @formatter:off
		return Arrays.asList(new Object[][] {
			// neither supported header nor supported format given, return default:
			{ fakeRequest(), null, /*->*/ "json" },
			{ fakeRequest(), "", /*->*/ "json" },
			{ fakeRequest(), "pdf", /*->*/ "json" },
			{ fakeRequest().header("Accept", ""), null, /*->*/ "json" },
			{ fakeRequest().header("Accept", "application/pdf"), null, /*->*/ "json" },
			// no header, just format parameter:
			{ fakeRequest(), "html", /*->*/ "html" },
			{ fakeRequest(), "json", /*->*/ "json" },
			{ fakeRequest(), "rdf", /*->*/ "rdf" },
			{ fakeRequest(), "ttl", /*->*/ "ttl" },
			{ fakeRequest(), "nt", /*->*/ "nt" },
			// supported content types, no format parameter given:
			{ fakeRequest().header("Accept", "text/html"), null, /*->*/ "html" },
			{ fakeRequest().header("Accept", "application/json"), null, /*->*/ "json" },
			{ fakeRequest().header("Accept", "application/ld+json"), null, /*->*/ "json" },
			{ fakeRequest().header("Accept", "text/plain"), null, /*->*/ "nt" },
			{ fakeRequest().header("Accept", "application/n-triples"), null, /*->*/ "nt" },
			{ fakeRequest().header("Accept", "text/turtle"), null, /*->*/ "ttl" },
			{ fakeRequest().header("Accept", "application/x-turtle"), null, /*->*/ "ttl" },
			{ fakeRequest().header("Accept", "application/xml"), null, /*->*/ "rdf" },
			{ fakeRequest().header("Accept", "application/rdf+xml"), null, /*->*/ "rdf" },
			{ fakeRequest().header("Accept", "text/xml"), null, /*->*/ "rdf" },
			// we pick the preferred content type:
			{ fakeRequest().header("Accept", "text/html,application/json"), null, /*->*/"html" },
			{ fakeRequest().header("Accept", "application/json,text/html"), null, /*->*/ "json" },
			// format parameter overrides header:
			{ fakeRequest().header("Accept", "text/html"), "json", /*->*/ "json" }});
	} // @formatter:on

	private Http.RequestBuilder fakeRequest;
	private String passedFormat;
	private String expectedFormat;

	public AcceptUnitTest(Http.RequestBuilder request, String givenFormat,
			String expectedFormat) {
		this.fakeRequest = request;
		this.passedFormat = givenFormat;
		this.expectedFormat = expectedFormat;
	}

	@Test
	public void test() {
		Collection<MediaRange> acceptedTypes = fakeRequest.build().acceptedTypes();
		String description =
				String.format("resulting format for passedFormat=%s, acceptedTypes=%s",
						passedFormat, acceptedTypes);
		String result = Accept.formatFor(passedFormat, acceptedTypes);
		assertThat(result).as(description).startsWith(expectedFormat);
	}

}