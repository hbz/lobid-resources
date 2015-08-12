/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Test;

import controllers.nwbib.Classification;
import controllers.nwbib.Lobid;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaTest
 */
@SuppressWarnings("javadoc")
public class ApplicationTest {

	@Test
	public void shortClassificationId() {
		assertThat(Classification.shortId("http://purl.org/lobid/nwbib#s58206"))
				.as("short classification").isEqualTo("58206");
	}

	@Test
	public void shortSpatialClassificationId() {
		assertThat(
				Classification.shortId("http://purl.org/lobid/nwbib-spatial#n58"))
						.as("short spatial classification").isEqualTo("58");
	}

	@Test
	public void typeSelectionMultiVolumeBook() {
		String selected = Lobid.selectType(
				Arrays.asList("http://purl.org/dc/terms/BibliographicResource",
						"http://purl.org/ontology/bibo/MultiVolumeBook",
						"http://purl.org/ontology/bibo/Book"),
				"type.labels");
		assertThat(selected)
				.isEqualTo("http://purl.org/ontology/bibo/MultiVolumeBook");
	}

	@Test
	public void typeSelectionPublishedScore() {
		String selected = Lobid
				.selectType(Arrays.asList("http://purl.org/ontology/mo/PublishedScore",
						"http://purl.org/ontology/bibo/MultiVolumeBook",
						"http://purl.org/ontology/bibo/Book"), "type.labels");
		assertThat(selected)
				.isEqualTo("http://purl.org/ontology/mo/PublishedScore");
	}

	@Test
	public void typeSelectionEditedVolume() {
		String selected =
				Lobid.selectType(
						Arrays.asList("http://purl.org/lobid/lv#EditedVolume",
								"http://purl.org/ontology/bibo/MultiVolumeBook",
								"http://purl.org/dc/terms/BibliographicResource"),
				"type.labels");
		assertThat(selected).isEqualTo("http://purl.org/lobid/lv#EditedVolume");
	}

}
