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
public class IndexIntegrationTest {

	// test data parameters, formatted as "input /*->*/ expected output"
	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		// @formatter:off
		return Arrays.asList(new Object[][] {
			{ "title:der", /*->*/ 2760162 },
			{ "title:Typee", /*->*/ 41 },
			{ "contribution.agent.label:Melville", /*->*/ 1859 },
			{ "contribution.agent.id:\"http\\://d-nb.info/gnd/118580604\"", /*->*/ 651 },
			{ "contribution.agent.id:118580604", /*->*/ 651 },
			{ "title:Typee AND contribution.agent.label:Melville", /*->*/ 37 },
			{ "title:Typee OR title:Moby", /*->*/ 372 },
			{ "(title:Typee OR title:Moby) AND contribution.agent.id:\"http\\://d-nb.info/gnd/118580604\"", /*->*/ 234 },
			{ "(title:Typee OR title:Moby) AND NOT contribution.agent.id:\"http\\://d-nb.info/gnd/118580604\"", /*->*/ 372 - 234 },
			{ "subject.label:Bahnhof", /*->*/ 633 },
			{ "subject.id:\"http\\://d-nb.info/gnd/1113670827\"", /*->*/ 1 },
			{ "subject.id:1113670827", /*->*/ 0 },
			{ "subject.type:PlaceOrGeographicName", /*->*/ 1893899 },
			{ "publication.location:Tokyo", /*->*/ 35573 },
			{ "publication.startDate:1992", /*->*/ 274518 },
			{ "publication.location:Tokyo AND publication.startDate:1992", /*->*/ 659 },
			{ "publication.location:Tokyo AND publication.startDate:1992-1997", /*->*/ 1164 },
			{ "collectedBy.id:\"http\\://lobid.org/resources/NWBib\"", /*->*/ 385632 },
			{ "collectedBy.id:NWBib", /*->*/ 0 },
			{ "publication.publishedBy:Springer", /*->*/ 287329 },
			{ "exemplar.id:\"http\\://lobid.org/items/HT019073978\\:DE-5-225\\:SoP%202410%202016%2F2#\\!\"", /*->*/ 1 },
			{ "exemplar.id:DE-5-225", /*->*/ 0 }
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
			long actualResultCount = index.queryResources(queryString).getTotal();
			assertThat(actualResultCount).isEqualTo(expectedResultCount);
		});
	}

}