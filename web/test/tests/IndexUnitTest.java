/* Copyright 2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;

import controllers.resources.Index;

/**
 * Unit tests for functionality provided by the {@link Index} class.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
public class IndexUnitTest {

	private Index index = new Index();

	@Test
	public void testBuildQueryString() {
		assertThat(index.buildQueryString("")).isEqualTo("*");
		assertThat(index.buildQueryString("", "Melville"))
				.isEqualTo("* AND (contribution.agent.label:Melville)");
		assertThat(index.buildQueryString("", "http://d-nb.info/gnd/118580604"))
				.contains("contribution.agent.id");
		assertThat(index.buildQueryString("", "", "", "", "", "", "1999-2001"))
				.isEqualTo("* AND (publication.startDate:[1999 TO 2001])");
		assertThat(index.buildQueryString("", "", "", "", "", "", "1999-*"))
				.isEqualTo("* AND (publication.startDate:[1999 TO *])");
		assertThat(index.buildQueryString("", "", "", "", "", "", "*-2001"))
				.isEqualTo("* AND (publication.startDate:[* TO 2001])");
		assertThat(index.buildQueryString("", "", "", "", "", "", "*-*"))
				.isEqualTo("* AND (publication.startDate:[* TO *])");
		assertThat(index.buildQueryString("", "", "", "", "123"))
				.isEqualTo("* AND (isbn:123 OR (issn:123))");
		assertThat(index.buildQueryString("", "", "", "", "123,456"))
				.isEqualTo("* AND (isbn:123 AND isbn:456 OR (issn:123 AND issn:456))");
		assertThat(index.buildQueryString("", "Melville", "", "", "123,456"))
				.isEqualTo(
						"* AND (contribution.agent.label:Melville) AND (isbn:123 AND isbn:456 OR (issn:123 AND issn:456))");
	}

}