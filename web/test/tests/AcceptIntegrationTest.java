/* Copyright 2014-2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.header;
import static play.test.Helpers.route;
import static play.test.Helpers.running;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import controllers.resources.Accept;
import play.mvc.Result;
import play.test.FakeRequest;

/**
 * Integration tests for functionality provided by the {@link Accept} class.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class AcceptIntegrationTest extends LocalIndexSetup {

	// test data parameters, formatted as "input /*->*/ expected output"
	@Parameters
	public static Collection<Object[]> data() {
		// @formatter:off
		return Arrays.asList(new Object[][] {
			// search, default format: JSON
			{ fakeRequest(GET, "/resources/search?q=*"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/resources/search?q=*&format="), /*->*/ "application/json" },
			{ fakeRequest(GET, "/resources/search?q=*&format=json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/resources/search?q=*&format=whatever"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/resources/search?q=*").withHeader("Accept", "text/plain"), /*->*/ "application/json" },
			// search, others formats as query param:
			{ fakeRequest(GET, "/resources/search?q=*&format=html"), /*->*/ "text/html" },
			// search, others formats via header:
			{ fakeRequest(GET, "/resources/search?q=*").withHeader("Accept", "application/json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/resources/search?q=*").withHeader("Accept", "text/html"), /*->*/ "text/html" },
			// get, default format: JSON
			{ fakeRequest(GET, "/resources/HT018907266"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/resources/HT018907266?format="), /*->*/ "application/json" },
			{ fakeRequest(GET, "/resources/HT018907266?format=json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/resources/HT018907266?format=whatever"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/resources/HT018907266").withHeader("Accept", "text/plain"), /*->*/ "application/json" },
			// get, others formats as query param:
			{ fakeRequest(GET, "/resources/HT018907266?format=html"), /*->*/ "text/html" },
			// get, others formats via header:
			{ fakeRequest(GET, "/resources/HT018907266").withHeader("Accept", "application/json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/resources/HT018907266").withHeader("Accept", "text/html"), /*->*/ "text/html" }});
	} // @formatter:on

	private FakeRequest fakeRequest;
	private String contentType;

	public AcceptIntegrationTest(FakeRequest request, String contentType) {
		this.fakeRequest = request;
		this.contentType = contentType;
	}

	@Test
	public void test() {
		running(fakeApplication(), () -> {
			Result result = route(fakeRequest);
			assertThat(result).isNotNull();
			assertThat(contentType(result)).isEqualTo(contentType);
			if (contentType.equals("application/json")) {
				assertThat(header("Access-Control-Allow-Origin", result))
						.isEqualTo("*");
			}
		});
	}

}