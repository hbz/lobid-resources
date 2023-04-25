/* Copyright 2017 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

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
			{ "/resources/99371123630706441", /*->*/ Status.OK },
			{ "/items/99371123630706441:DE-Hag4:5388892840006461#!", /*->*/ Status.SEE_OTHER },
			{ "/resources/123", /*->*/ Status.NOT_FOUND },
			{ "/items/123", /*->*/ Status.SEE_OTHER },
			{ "/resources/123?format=html", /*->*/ Status.NOT_FOUND },
			{ "/items/123?format=html", /*->*/ Status.SEE_OTHER },
			{ "/resources/search?q=*", /*->*/ Status.OK },
			{ "/resources/search?q=test", /*->*/ Status.OK },
			{ "/resources/search?word=*", /*->*/ Status.OK },
			{ "/resources/search?word=test", /*->*/ Status.OK },
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
