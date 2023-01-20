/* Copyright 2017 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import controllers.resources.Queries;
import controllers.resources.Search;
import play.Logger;

/**
 * Integration tests for functionality provided by the {@link Search} class.
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
		return queries(new Object[][] {
			{ "title:der", /*->*/ 27 },
			{ "title:Westfalen", /*->*/ 6 },
			{ "contribution.agent.label:Westfalen", /*->*/ 9 },
			{ "contribution.agent.label:Westfälen", /*->*/ 9 },
			{ "contribution.agent.id:\"https\\://d-nb.info/gnd/5265186-1\"", /*->*/ 1 },
			{ "contribution.agent.id:5265186-1", /*->*/ 0 },
			{ "contribution.agent.id:\"5265186-1\"", /*->*/ 0 },
			{ "title:Westfalen AND contribution.agent.label:Westfalen", /*->*/ 3 },
			{ "title:Westfalen OR title:Munsterland", /*->*/ 7 },
			{ "(title:Westfalen OR title:Münsterland) AND contribution.agent.id:\"https\\://d-nb.info/gnd/2019209-5\"", /*->*/ 0 },
			{ "subject.componentList.label:Münsterland", /*->*/ 2 },
			{ "subject.componentList.label:Muensterland", /*->*/ 2 },
			{ "subject.componentList.label:Munsterland", /*->*/ 2 },
			{ "subject.componentList.label:Münsterländer", /*->*/ 2 },
			{ "subject.componentList.label.unstemmed:Münsterländer", /*->*/ 0 },
			{ "subjectAltLabel:Südwestfalen", /*->*/ 1 },
			{ "subjectAltLabel:Suedwestfalen", /*->*/ 1 },
			{ "subjectAltLabel:Sudwestfalen", /*->*/ 1 },
			{ "subjectAltLabel:Südwestfale", /*->*/ 1 },
			{ "subjectAltLabel.unstemmed:Südwestfale", /*->*/ 0 },
			{ "subject.componentList.id:\"https\\://d-nb.info/gnd/4042570-8\"", /*->*/ 6 },
			{ "(title:Westfalen OR title:Münsterland) AND NOT contribution.agent.id:\"https\\://d-nb.info/gnd/2019209-5\"", /*->*/ 7 },
			{ "subject.componentList.label:Westfalen", /*->*/ 12 },
			{ "subject.componentList.label:Westfälen", /*->*/ 12 },
			{ "spatial.label:Westfalen", /*->*/ 14 },
			{ "spatial.label:Westfälen", /*->*/ 14 },
			{ "subject.componentList.id:\"https\\://d-nb.info/gnd/4042570-8\"", /*->*/ 6 },
			{ "subject.componentList.id:1113670827", /*->*/ 0 },
			{ "subject.componentList.type:PlaceOrGeographicName", /*->*/ 38 },
			{ "publication.location:Berlin", /*->*/ 19 },
			{ "subject.notation:914.3", /*->*/ 5 },
			{ "subject.notation:914", /*->*/ 0 },
			{ "subject.notation:914*", /*->*/ 5 },
			{ "publication.location:Köln", /*->*/ 12 },
			{ "publication.location:Koln", /*->*/ 12 },
			{ "publication.startDate:1993", /*->*/ 3 },
			{ "publication.location:Berlin AND publication.startDate:1993", /*->*/ 1 },
			{ "publication.location:Berlin AND publication.startDate:[1992 TO 2017]", /*->*/ 14 },
			{ "inCollection.id:\"http\\://lobid.org/resources/HT014176012#\\!\"", /*->*/ 52 },
			{ "inCollection.id:NWBib", /*->*/ 0 },
			{ "publication.publishedBy:Springer", /*->*/ 4 },
			{ "publication.publishedBy:Spring", /*->*/ 4 },
			{ "publication.publishedBy:DAG", /*->*/ 1 },
			{ "publication.publishedBy:DÄG", /*->*/ 1 },
			{ "hasItem.id:\"http\\://lobid.org/items/TT003059252\\:DE-5-58\\:9%2F041#\\!\"", /*->*/ 1 },
			{ "hasItem.id:TT003059252\\:DE-5-58\\:9%2F041", /*->*/ 0 },
			{ "coverage:99", /*->*/ 16},
			{ "isbn:3454128013", /*->*/ 1},
			{ "isbn:345-4128-013", /*->*/ 1},
			{ "\"Studies in social and political theory\"", /*->*/ 1},
			{ "(+Studies +in +social +and +political +theory)", /*->*/ 1},
			{ "\"Zeitzeuge und Kleinod in Harsewinkel\"", /*->*/ 1},
			{ "(+Zeitzeuge +und +Kleinod +in +Harsewinkel)", /*->*/ 1},
			{ "\"Mülheim an der Ruhr\"", /*->*/ 1},
			{ "(+Mülheim +an +der +Ruhr)", /*->*/ 1},
			{ "\"Amtliche Publikation\"", /*->*/ 3}
		});
	} // @formatter:on

	private static List<Object[]> queries(Object[][] objects) {
		List<Object[]> result = new ArrayList<>();
		for (Object[] testCase : objects) {
			String s = (String) testCase[0];
			Integer hits = (Integer) testCase[1];
			result.add(new Object[] { new Queries.Builder().q(s), /*->*/ hits });
			result.add(new Object[] { new Queries.Builder().word(s), /*->*/ hits });
		}
		return result;
	}

	private int expectedResultCount;
	private Search index;

	public IndexIntegrationTest(Queries.Builder query, int resultCount) {
		this.expectedResultCount = resultCount;
		this.index = new Search.Builder().query(query.build()).build();
	}

	@Test
	public void testResultCount() {
		running(fakeApplication(), () -> {
			long totalHits = index.totalHits();
			Logger.debug("{}", index.getResult());
			assertThat(totalHits).isEqualTo(expectedResultCount);
		});
	}

}
