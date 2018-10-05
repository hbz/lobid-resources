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
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;

import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.Status;
import play.mvc.Result;
import play.test.Helpers;

/**
 * Test customizable aggregations.
 * 
 * See https://github.com/hbz/lobid-resources/pull/243
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class AggregationsTest extends LocalIndexSetup {

	// test data parameters, formatted as "input /*->*/ expected output"
	@Parameters(name = "{0} -> {1}")
	public static Collection<Object[]> data() {
		// @formatter:off
		return Arrays.asList(new Object[][] {
			// neither supported header nor supported format given, return default:
			{ "", /*->*/ 0, Status.OK },
			{ "&aggregations=", /*->*/ 0, Status.OK },
			{ "&aggregations=type", /*->*/ 1, Status.OK },
			{ "&aggregations=type,subject.componentList.id", /*->*/ 2, Status.OK },
			{ "&aggregations=invalid", /*->*/ 0, Status.BAD_REQUEST },});
	} // @formatter:on

	private Http.RequestBuilder fakeRequest;
	private int expectedNumberOfAggragations;
	private int expectedResponseStatus;

	public AggregationsTest(String param, int expectedNumberOfAggragations,
			int status) {
		this.fakeRequest =
				fakeRequest(GET, "/resources/search?format=json&q=*" + param);
		this.expectedNumberOfAggragations = expectedNumberOfAggragations;
		this.expectedResponseStatus = status;
	}

	@Test
	public void test() {
		running(fakeApplication(), () -> {
			Result route = route(fakeRequest);
			int responseStatus = route.status();
			assertThat(responseStatus).as("response status")
					.isEqualTo(expectedResponseStatus);
			if (responseStatus == Status.OK) {
				JsonNode aggregationNode =
						Json.parse(Helpers.contentAsString(route)).findValue("aggregation");
				String aggregationsQueryParam =
						fakeRequest.build().getQueryString("aggregations");
				if (aggregationsQueryParam == null
						|| aggregationsQueryParam.isEmpty()) {
					assertThat(aggregationNode).isNull();
				} else {
					int numberOfAggregations =
							Json.fromJson(aggregationNode, Map.class).keySet().size();
					assertThat(numberOfAggregations)
							.as("resulting number of aggregations")
							.isEqualTo(expectedNumberOfAggragations);
				}
			}
		});

	}

}