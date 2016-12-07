/* Copyright 2014 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static controllers.nwbib.Application.CONFIG;
import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

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
	public void classificationLabelNotAvailable() {
		assertThat(Classification.label("http://purl.org/lobid/nwbib-spatial#n58",
				"no-type")).as("empty label").isEqualTo("");
	}

	@Test
	public void typeSelectionMultiVolumeBook() {
		String selected = Lobid.selectType(
				Arrays.asList("BibliographicResource", "MultiVolumeBook", "Book"),
				"type.labels");
		assertThat(selected).isEqualTo("MultiVolumeBook");
	}

	@Test
	public void typeSelectionPublishedScore() {
		String selected = Lobid.selectType(
				Arrays.asList("MultiVolumeBook", "PublishedScore", "Book"),
				"type.labels");
		assertThat(selected).isEqualTo("PublishedScore");
	}

	@Test
	public void typeSelectionEditedVolume() {
		String selected = Lobid.selectType(Arrays.asList("MultiVolumeBook",
				"BibliographicResource", "EditedVolume"), "type.labels");
		assertThat(selected).isEqualTo("EditedVolume");
	}

	@Test
	public void classificationNwbibsubject()
			throws MalformedURLException, IOException {
		List<String> nwbibsubjects = Classification
				.toJsonLd(new URL(CONFIG.getString("index.data.nwbibsubject")));
		nwbibsubjects.forEach(System.out::println);
		assertThat(nwbibsubjects.size()).isEqualTo(998);
		assertThat(nwbibsubjects.toString()).contains("Allgemeine Landeskunde")
				.contains("Landesbeschreibungen").contains("Reiseberichte");
	}

	@Test
	public void classificationNwbibspatial()
			throws MalformedURLException, IOException {
		List<String> nwbibspatials = Classification
				.toJsonLd(new URL(CONFIG.getString("index.data.nwbibspatial")));
		nwbibspatials.forEach(System.out::println);
		assertThat(nwbibspatials.size()).isEqualTo(45);
		assertThat(nwbibspatials.toString()).contains("Nordrhein-Westfalen")
				.contains("Rheinland").contains("Grafschaft, Herzogtum Jülich");
	}

}
