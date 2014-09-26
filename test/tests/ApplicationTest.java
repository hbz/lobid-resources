/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;

import controllers.nwbib.Classification;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaTest
 */
public class ApplicationTest {

	@Test
	public void shortClassificationId() {
		assertThat(Classification.shortId("http://purl.org/lobid/nwbib#s58206"))
				.as("short classification").isEqualTo("58206");
	}

	@Test
	public void shortSpatialClassificationId() {
		assertThat(
				Classification
						.shortId("http://purl.org/lobid/nwbib-spatial#n58"))
				.as("short spatial classification").isEqualTo("58");
	}

}
