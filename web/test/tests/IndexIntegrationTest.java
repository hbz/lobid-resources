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
			{ "title:der", /*->*/ 23 },
			{ "title:Westfalen", /*->*/ 8 },
			{ "contribution.agent.label:Westfalen", /*->*/ 5 },
			{ "contribution.agent.label:Westfälen", /*->*/ 5 },
			{ "contribution.agent.id:\"https\\://d-nb.info/gnd/5253963-5\"", /*->*/ 1 },
			{ "contribution.agent.gndIdentifier:5253963-5", /*->*/ 1 },
			{ "contribution.agent.id:5265186-1", /*->*/ 0 },
			{ "contribution.agent.id:\"5265186-1\"", /*->*/ 0 },
			{ "title:Westfalen AND contribution.agent.label:Prause", /*->*/ 1 },
			{ "title:Westfalen OR title:Munsterland", /*->*/ 8 },
			{ "(title:Westfalen OR title:Münsterland) AND contribution.agent.id:\"https\\://d-nb.info/gnd/5253963-5\"", /*->*/ 0 },
			{ "bibliographicLevel.label.raw:\"Monographic component part\"", /*->*/ 17 },
			{ "subject.componentList.label:Düsseldorf", /*->*/ 1 },
			{ "subject.componentList.label:Duesseldorf", /*->*/ 1 },
			{ "subject.componentList.label:Dusseldorf", /*->*/ 1 },
			{ "subject.componentList.label:Düsseldorfer", /*->*/ 1 },
			{ "subject.componentList.label.unstemmed:Düsseldorfer", /*->*/ 0 },
			{ "subject.componentList.id:\"https\\://d-nb.info/gnd/4042570-8\"", /*->*/ 3 },
			{ "subject.componentList.gndIdentifier:4042570-8", /*->*/ 3 },
			{ "subject=Eisstockclub Lauterecken", /*->*/ 1 },
			{ "subject=http://rpb.lobid.org/sw/n920756", /*->*/ 1 },
			{ "(title:Westfalen OR title:Münsterland) AND NOT contribution.agent.id:\"https\\://d-nb.info/gnd/2019209-5\"", /*->*/ 8 },
			{ "subject.componentList.label:Westfalen", /*->*/ 12 },
			{ "subject.componentList.label:Westfälen", /*->*/ 12 },
			{ "spatial.label:Westfalen", /*->*/ 10 },
			{ "spatial.label:Westfälen", /*->*/ 10 },
			{ "subject.componentList.id:1113670827", /*->*/ 0 },
			{ "subject.componentList.type:PlaceOrGeographicName", /*->*/ 34 },
			{ "publication.location:Berlin", /*->*/ 17 },
			{ "subject.gndIdentifier:4040795-0", /*->*/ 1 },
			{ "subject.notation:914.3", /*->*/ 8 },
			{ "subject.notation:914", /*->*/ 0 },
			{ "subject.notation:914*", /*->*/ 8 },
			{ "publication.location:Köln", /*->*/ 7 },
			{ "publication.location:Koln", /*->*/ 7 },
			{ "publication.startDate:1993", /*->*/ 4 },
			{ "publication.location:Berlin AND publication.startDate:1993", /*->*/ 1 },
			{ "inCollection.id:\"http\\://lobid.org/organisations/DE-655#\\!\"", /*->*/ 191 },
			{ "inCollection.id:\"https\\://nrw.digibib.net/search/hbzvk/\"", /*->*/ 213 },
			{ "inCollection.id:NWBib", /*->*/ 0 },
			{ "publication.publishedBy:Quedenfeldt", /*->*/ 2 },
			{ "publication.publishedBy:Quedenfeld", /*->*/ 2 },
			{ "publication.publishedBy:Fidula", /*->*/ 1 },
			{ "publication.publishedBy:Fidüla", /*->*/ 1 },
			{ "hasItem.id:\"http\\://lobid.org/items/990021367710206441\\:DE-290\\:23198604440006445#\\!\"", /*->*/ 1 },
			{ "hasItem.id:990021367710206441\\:DE-290\\:23198604440006445", /*->*/ 0 },
			{ "hasItem.callNumber:\"5200/Mars\"", /*->*/ 1 },
			{ "hasItem.callNumber:Hist*", /*->*/ 1 },
			{ "hasItem.serialNumber:20098056", /*->*/ 1 },
			{ "isbn:9780702075551", /*->*/ 1},
			{ "isbn:070-2075-558", /*->*/ 1},
			{ "isbn:0702075558", /*->*/ 1},
			{ "related.issn:07206763", /*->*/ 1 },
			{ "related.issn:0720\\-6763", /*->*/ 1 },
			{ "issn:21914664", /*->*/ 1 },
			{ "issn:2191\\-4664", /*->*/ 1 },
			{ "\"Handbook on policy, process and governing\"", /*->*/ 1},
			{ "(+Handbook +on +policy +process +and +governing)", /*->*/ 1},
			{ "\"Mülheim an der Ruhr\"", /*->*/ 1},
			{ "(+Mülheim +an +der +Ruhr)", /*->*/ 1},
			{ "\"Amtliche Publikation\"", /*->*/ 1},
			{ "describedBy.resultOf.object.dateCreated:\"2023-03-22\"", /*->*/ 1},
			{ "describedBy.resultOf.object.dateModified:\"2023-07-30\"", /*->*/ 3},
			{ "describedBy.resultOf.object.sourceOrganization.id:\"http\\://lobid.org/organisations/DE-5#\\!\"", /*->*/ 8},
			{ "describedBy.resultOf.object.modifiedBy.id:\"http\\://lobid.org/organisations/DE-6#\\!\"", /*->*/ 21 },
			{ "\"Reader-friendly\"", /*->*/ 1},
			{ "\"Reader friendly\"", /*->*/ 1},
			// all q tests are related to DigiBib
			{ "q.date:2000", /*->*/ 4 },
			{ "q.provenance:\"https\\://d-nb.info/gnd/131844024\"", /*->*/ 1 },
			{ "q.provenance:hoboken", /*->*/ 1 },
			{ "q.provenance:stempel", /*->*/ 2 },
			{ "q.provenance:regi*", /*->*/ 2 },
			{ "q.provenance:\"Sk 5130\"", /*->*/ 1 },
			{ "q.publisher:Aachen", /*->*/ 2 },
			{ "q.publisher:Aachen\\-Eilendorf", /*->*/ 1 },
			{ "q.publisher:Eilendörf", /*->*/ 1 },
			{ "q.publisher:\"Aachen Eilendorf\"", /*->*/ 0 },
			{ "q.publisher:Quedenfeldt", /*->*/ 2 },
			{ "q.publisher:Quedenfeld", /*->*/ 0 },
			{ "q.publisher:Quedenfeld*", /*->*/ 2 },
			{ "q.subject:Düsseldorf", /*->*/ 1 },
			{ "q.subject:Duesseldorf", /*->*/ 1 },
			{ "q.subject:Dusseldorf", /*->*/ 1 },
			{ "q.subject:Düsseldorfer", /*->*/ 1 },
			{ "q.subject:Westfalen", /*->*/ 13 },
			{ "q.subject:Westfälen", /*->*/ 13 },
			{ "q.subject:Lithuania", /*->*/ 1 },
			{ "q.subject:Baukem", /*->*/ 2 },
			{ "q.subject:\"https\\://d-nb.info/gnd/4040795-0\"", /*->*/ 1 },
			{ "q.subject:\"https\\://d-nb.info/gnd/4042570-8\"", /*->*/ 3 },
			{ "q.subject:4040795-0", /*->*/ 1 },
			{ "q.subject:4042570-8", /*->*/ 3 },
			{ "q.title:der", /*->*/ 0 },
			{ "q.title:Westfalen", /*->*/ 8 },
			{ "q.title:Eilendorf", /*->*/ 1 },
			{ "q.all:Federale", /*->*/ 6 },
			{ "q.all:Fédérale", /*->*/ 6 },
			{ "q.all:(Courtillon cinema)", /*->*/ 1 },
			{ "q.all:(Courtillon cinéma)", /*->*/ 1 },
			{ "q.all:\"https\\://d-nb.info/gnd/4040795-0\"", /*->*/ 1 },
			{ "q.all:\"https\\://d-nb.info/gnd/4042570-8\"", /*->*/ 4 },
			{ "q.all:\"https\\://d-nb.info/gnd/5253963-5\"", /*->*/ 1 },
			{ "q.all:4040795-0", /*->*/ 1 },
			{ "q.all:4042570-8", /*->*/ 4 },
			{ "q.all:5253963-5", /*->*/ 1 },
			{ "q.all:0702075558", /*->*/ 1 },
			{ "q.all:07\\-0207\\-555\\-8", /*->*/ 1 }, // search with hyphens possible due to digibib_standardnumber
			{ "q.all:07206763", /*->*/ 1 },
			{ "q.all:0720\\-6763", /*->*/ 0 }, // search with hyphens not possible, different to lobid general search
			{ "q.all:21914664", /*->*/ 1 },
			{ "q.all:2191\\-4664", /*->*/ 0 }, // search with hyphens not possible, different to lobid general search
			{ "q.all:HT072067630", /*->*/ 0 },
			{ "q.all:(Erleben \\- Verstehen & Lernen)", /*->*/ 5 },
			{ "q.all:(Lexicography \\: Selected Papers)", /*->*/ 1 },
			{ "contribution.agent.label.digibib:Westfalen", /*->*/ 5 },
			{ "contribution.agent.label.digibib:Westfälen", /*->*/ 5 },
			{ "contribution.agent.label.digibib:Westfälisch", /*->*/ 5 },
			{ "contribution.agent.label.digibib:Praus", /*->*/ 1 },
			{ "contribution.agent.label.digibib_unstemmed:Westfalen", /*->*/ 2 },
			{ "contribution.agent.label.digibib_unstemmed:Westfälen", /*->*/ 2 },
			{ "contribution.agent.label.digibib_unstemmed:Westfälisch", /*->*/ 0 },
			{ "contribution.agent.label.digibib_unstemmed:Praus", /*->*/ 0 },
			{ "contribution.agent.altLabel.digibib:Nemačke", /*->*/ 1 },
			{ "contribution.agent.altLabel.digibib:Nemack", /*->*/ 1 },
			{ "contribution.agent.altLabel.digibib_unstemmed:Nemačke", /*->*/ 1 },
			{ "contribution.agent.altLabel.digibib_unstemmed:Nemack", /*->*/ 0 },
			{ "exampleOfWork.language.id:\"http\\://id.loc.gov/vocabulary/iso639-2/eng\"", /*->*/ 2 },
			{ "hasItem.inCollection.id:\"http://lobid.org/organisations/DE-468#!\"", /*->*/ 29 },
			{ "accessRights.id:\"http://purl.org/coar/access_right/c_abf2\"", /*->*/ 8 }
		});
	} // @formatter:on

	private static List<Object[]> queries(Object[][] objects) {
		List<Object[]> result = new ArrayList<>();
		for (Object[] testCase : objects) {
			String s = (String) testCase[0];
			Integer hits = (Integer) testCase[1];
			result.add(new Object[] { queryFor(s), /*->*/ hits });
		}
		return result;
	}

	private static Queries.Builder queryFor(String s) {
		String[] queryParamNameAndValue = s.split("=");
		Queries.Builder queryBuilder = new Queries.Builder();
		return queryParamNameAndValue.length == 2
				&& queryParamNameAndValue[0].equals("subject")
						? queryBuilder.subject(queryParamNameAndValue[1])
						: queryBuilder.q(queryParamNameAndValue[0]);
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
			try {
				long totalHits = index.totalHits();
				Logger.debug("{}", index.getResult());
				assertThat(totalHits).isEqualTo(expectedResultCount);
			}
			catch (RuntimeException e) {
				assertThat(-1).isEqualTo(expectedResultCount);
			}
		});
	}

}
