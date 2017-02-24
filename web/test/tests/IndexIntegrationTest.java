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
 * Integration tests for functionality provided by the {@link Index} class.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class IndexIntegrationTest extends LocalIndexSetup {

	// test data parameters, formatted as "input /*->*/ expected output"
	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		// @formatter:off
		return Arrays.asList(new Object[][] {
			{ "title:der", /*->*/ 18 },
			{ "title:Westfalen", /*->*/ 3 },
			{ "contribution.agent.label:Westfalen", /*->*/ 7 },
			{ "contribution.agent.id:\"http\\://d-nb.info/gnd/5265186-1\"", /*->*/ 1 },
			{ "contribution.agent.id:5265186-1", /*->*/ 3 }, // TODO: don't tokenize at dashes
			{ "contribution.agent.id:\"5265186-1\"", /*->*/ 1 },
			{ "title:Westfalen AND contribution.agent.label:Westfalen", /*->*/ 2 },
			{ "title:Westfalen OR title:Münsterland", /*->*/ 4 },
			{ "(title:Westfalen OR title:Münsterland) AND contribution.agent.id:\"http\\://d-nb.info/gnd/2019209-5\"", /*->*/ 1 },
			{ "(title:Westfalen OR title:Münsterland) AND NOT contribution.agent.id:\"http\\://d-nb.info/gnd/2019209-5\"", /*->*/ 4-1 },
			{ "subject.label:Westfalen", /*->*/ 6 },
			{ "subject.id:\"http\\://d-nb.info/gnd/4042570-8\"", /*->*/ 2 },
			{ "subject.id:1113670827", /*->*/ 0 },
			{ "subject.type:PlaceOrGeographicName", /*->*/ 25 },
			{ "publication.location:Berlin", /*->*/ 8 },
			{ "publication.startDate:1993", /*->*/ 2 },
			{ "publication.location:Berlin AND publication.startDate:1993", /*->*/ 1 },
			{ "publication.location:Berlin AND publication.startDate:[1992 TO 2017]", /*->*/ 7 },
			{ "collectedBy.id:\"http\\://lobid.org/resources/NWBib\"", /*->*/ 38 },
			{ "collectedBy.id:NWBib", /*->*/ 0 },
			{ "publication.publishedBy:Springer", /*->*/ 2 },
			{ "exemplar.id:\"http\\://lobid.org/items/TT003059252\\:DE-5-58\\:9%2F041#\\!\"", /*->*/ 1 },
			{ "exemplar.id:TT003059252\\:DE-5-58\\:9%2F041", /*->*/ 0 }
		});
	} // @formatter:on

	private String queryString;
	private int expectedResultCount;
	private Index index;

	public IndexIntegrationTest(String queryString, int resultCount) {
		this.queryString = queryString;
		this.expectedResultCount = resultCount;
		this.index = new Index();
	}

	@Test
	public void testResultCount() {
		running(fakeApplication(), () -> {
			assertThat(index.totalHits(queryString)).isEqualTo(expectedResultCount);
		});
	}

}