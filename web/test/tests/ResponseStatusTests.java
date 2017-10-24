/* Copyright 2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.GET;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.running;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import play.mvc.Http.Status;
import play.mvc.Result;

/**
 * Test response status codes for different requests.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class ResponseStatusTests extends LocalIndexSetup {

	// test data parameters, formatted as "input /*->*/ expected result"
	@Parameters(name = "{0} -> {1}")
	public static Collection<Object[]> data() {
		// @formatter:off
		return Arrays.asList(new Object[][] {
			{ "/resources/TT050409948", /*->*/ Status.OK },
			{ "/items/TT003059252:DE-5-58:9%2F041", /*->*/ Status.OK },
			{ "/resources/123", /*->*/ Status.NOT_FOUND },
			{ "/items/123", /*->*/ Status.NOT_FOUND },
			{ "/resources/123?format=html", /*->*/ Status.NOT_FOUND },
			{ "/items/123?format=html", /*->*/ Status.NOT_FOUND },
			{ "/resources/search?q=*", /*->*/ Status.OK },
			{ "/resources/search?q=[]", /*->*/ Status.BAD_REQUEST },
			{ "/resources/search?q=[]?format=html", /*->*/ Status.BAD_REQUEST }
		});
	} // @formatter:on

	private String request;
	private int expectedResponseStatus;

	public ResponseStatusTests(String request, int expectedResponseStatus) {
		this.request = request;
		this.expectedResponseStatus = expectedResponseStatus;
	}

	@Test
	public void testResponseStatus() {
		running(fakeApplication(), () -> {
			Result result = route(fakeRequest(GET, request));
			assertThat(result.status()).isEqualTo(expectedResponseStatus);
		});
	}

}