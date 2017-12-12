/* Copyright 2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import controllers.resources.Index;

/**
 * See http://www.playframework.com/documentation/2.4.x/JavaFunctionalTest
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class NestedQueryTests extends LocalIndexSetup {

	// test data parameters, formatted as "input /*->*/ expected output"
	@Parameters(name = "nested={0} q={1} -> {2}")
	public static Collection<Object[]> data() {
		// @formatter:off
		return Arrays.asList(new Object[][] {
			// Nested query: only return hits where query matches 1 nested pseudo-doc:
			{ "contribution", "contribution.agent.label:SCHOLLE AND contribution.role.label:Mitwirkende", /*->*/ 1 },
			// Nested query: don't match if query parts match different nested docs:
			{ "contribution", "contribution.agent.label:SCHOLLE AND contribution.role.label:Autor", /*->*/ 0 },
			// Normal query: return hits where query matches parent top-level doc:
			{ "", "contribution.agent.label:SCHOLLE AND contribution.role.label:Autor", /*->*/ 1 },
			// Same for 'subject' nested field:
			{ "subject", "subject.label:Westfalen AND subject.source.label:Raumsystematik", /*->*/ 1 },
			{ "subject", "subject.label:Westfalen AND subject.source.label:Sachsystematik", /*->*/ 0 },
			{ "", "subject.label:Westfalen AND subject.source.label:Sachsystematik", /*->*/ 1 },
			// Same for 'subject.componentList' nested field:
			{ "subject.componentList", 
				"subject.componentList.label:Freudenberg AND subject.componentList.type:PlaceOrGeographicName", /*->*/ 1 },
			{ "subject.componentList", 
				"subject.componentList.label:Freudenberg AND subject.componentList.type:SubjectHeading", /*->*/ 0 },
			{ "", "subject.componentList.label:Freudenberg AND subject.componentList.type:SubjectHeading", /*->*/ 1 }
		});
	} // @formatter:on

	private String nestedString;
	private String queryString;
	private int expectedResultCount;
	private Index index;

	public NestedQueryTests(String nestedString, String queryString,
			int resultCount) {
		this.nestedString = nestedString;
		this.queryString = queryString;
		this.expectedResultCount = resultCount;
		this.index = new Index();
	}

	@Test
	public void testResultCount() {
		running(fakeApplication(), () -> {
			if (expectedResultCount == 1)
				assertThat(hitsForQuery()).isGreaterThanOrEqualTo(expectedResultCount);
			else
				assertThat(hitsForQuery()).isEqualTo(expectedResultCount);
		});
	}

	private long hitsForQuery() {
		return nestedString.isEmpty() ? index.totalHits(queryString)
				: index
						.queryResources(queryString, 0, 1, "", "", "", "", nestedString, "")
						.getTotal();
	}

}
