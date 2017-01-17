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
			{ "title:der", /*->*/ 2760192 },
			{ "title:Typee", /*->*/ 41 }
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