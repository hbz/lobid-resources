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
	@Parameters(name = "({index}) {0}")
	public static Collection<Object[]> data() {
		// @formatter:off
		return queries(new Object[][] {
			{ "title:der", /*->*/ 13 },
			{ "title:Westfalen", /*->*/ 8 },
			{ "contribution.agent.label:Westfalen", /*->*/ 3 },
			{ "contribution.agent.label:Westfälen", /*->*/ 3 },
			{ "contribution.agent.id:\"https\\://d-nb.info/gnd/5253963-5\"", /*->*/ 1 },
			{ "contribution.agent.id:5265186-1", /*->*/ 0 },
			{ "contribution.agent.id:\"5265186-1\"", /*->*/ 0 },
			{ "title:Westfalen AND contribution.agent.label:Prause", /*->*/ 1 },
			{ "title:Westfalen OR title:Munsterland", /*->*/ 8 },
			{ "(title:Westfalen OR title:Münsterland) AND contribution.agent.id:\"https\\://d-nb.info/gnd/5253963-5\"", /*->*/ 0 },
			{ "bibliographicLevel.label.raw:\"Monographic component part\"", /*->*/ 14 },
			{ "subject.componentList.label:Düsseldorf", /*->*/ 1 },
			{ "subject.componentList.label:Duesseldorf", /*->*/ 1 },
			{ "subject.componentList.label:Dusseldorf", /*->*/ 1 },
			{ "subject.componentList.label:Düsseldorfer", /*->*/ 1 },
			{ "subject.componentList.label.unstemmed:Düsseldorfer", /*->*/ 0 },
			{ "subject.componentList.id:\"https\\://d-nb.info/gnd/4042570-8\"", /*->*/ 2 },
			{ "(title:Westfalen OR title:Münsterland) AND NOT contribution.agent.id:\"https\\://d-nb.info/gnd/2019209-5\"", /*->*/ 8 },
			{ "subject.componentList.label:Westfalen", /*->*/ 10 },
			{ "subject.componentList.label:Westfälen", /*->*/ 10 },
			{ "spatial.label:Westfalen", /*->*/ 7 },
			{ "spatial.label:Westfälen", /*->*/ 7 },
			{ "subject.componentList.id:1113670827", /*->*/ 0 },
			{ "subject.componentList.type:PlaceOrGeographicName", /*->*/ 20 },
			{ "publication.location:Berlin", /*->*/ 14 },
			{ "subject.notation:914.3", /*->*/ 6 },
			{ "subject.notation:914", /*->*/ 0 },
			{ "subject.notation:914*", /*->*/ 6 },
			{ "publication.location:Köln", /*->*/ 5 },
			{ "publication.location:Koln", /*->*/ 5 },
			{ "publication.startDate:1993", /*->*/ 3 },
			{ "publication.location:Berlin AND publication.startDate:1993", /*->*/ 1 },
			{ "publication.location:Berlin AND publication.startDate:[1992 TO 2017]", /*->*/ 5 },
			{ "inCollection.id:\"http\\://lobid.org/organisations/DE-655#\\!\"", /*->*/ 118 },
			{ "inCollection.id:NWBib", /*->*/ 0 },
			{ "publication.publishedBy:Quedenfeldt", /*->*/ 2 },
			{ "publication.publishedBy:Quedenfeld", /*->*/ 2 },
			{ "publication.publishedBy:Fidula", /*->*/ 1 },
			{ "publication.publishedBy:Fidüla", /*->*/ 1 },
			{ "hasItem.id:\"http\\://lobid.org/items/990021367710206441\\:DE-290\\:23198604440006445#\\!\"", /*->*/ 1 },
			{ "hasItem.id:990021367710206441\\:DE-290\\:23198604440006445", /*->*/ 0 },
			{ "hasItem.callNumber:\"5200/Mars\"", /*->*/ 1 },
			{ "hasItem.callNumber:Hist*", /*->*/ 1 },
			{ "hasItem.serialNumber:20098056", /*->*/ -1 },
			{ "isbn:9780702075551", /*->*/ 1},
			{ "isbn:070-2075-558", /*->*/ 1},
			{ "isbn:0702075558", /*->*/ 1},
			{ "\"Handbook on policy, process and governing\"", /*->*/ 1},
			{ "(+Handbook +on +policy +process +and +governing)", /*->*/ 1},
			{ "\"Mülheim an der Ruhr\"", /*->*/ 1},
			{ "(+Mülheim +an +der +Ruhr)", /*->*/ 1},
			{ "\"Amtliche Publikation\"", /*->*/ 1},
			{ "describedBy.resultOf.object.dateCreated:\"2023-03-22\"", /*->*/ 1},
			{ "describedBy.resultOf.object.dateModified:\"2022-07-18\"", /*->*/ 1},
			{ "describedBy.resultOf.object.sourceOrganization.id:\"http\\://lobid.org/organisations/DE-5#\\!\"", /*->*/ 4},
			{ "describedBy.resultOf.object.modifiedBy.id:\"http\\://lobid.org/organisations/DE-6#\\!\"", /*->*/ 14 },
			{ "\"Reader-friendly\"", /*->*/ 1},
			{ "\"Reader friendly\"", /*->*/ 1}
		});
	} // @formatter:on

	private static List<Object[]> queries(Object[][] objects) {
		List<Object[]> result = new ArrayList<>();
		for (Object[] testCase : objects) {
			String s = (String) testCase[0];
			Integer hits = (Integer) testCase[1];
			result.add(new Object[] { new Queries.Builder().q(s), /*->*/ hits });
		}
		return result;
	}

	private int expectedResultCount;
	private Search index;

	public IndexIntegrationTest(Queries.Builder query, int resultCount) {
        Logger.debug(query.build().toString());
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
