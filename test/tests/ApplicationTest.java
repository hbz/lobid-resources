/* Copyright 2014-2016 Fabian Steeg, hbz. Licensed under the GPLv2 */

package tests;

import static org.fest.assertions.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Test;

import controllers.nwbib.Lobid;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaTest
 */
@SuppressWarnings("javadoc")
public class ApplicationTest {

	@Test
	public void typeSelectionMultiVolumeBook() {
		String selected = Lobid.selectType(
				Arrays.asList("BibliographicResource", "MultiVolumeBook", "Book"),
				"type.labels.lobid2");
		assertThat(selected).isEqualTo("MultiVolumeBook");
	}

	@Test
	public void typeSelectionPublishedScore() {
		String selected = Lobid.selectType(
				Arrays.asList("MultiVolumeBook", "PublishedScore", "Book"),
				"type.labels.lobid2");
		assertThat(selected).isEqualTo("PublishedScore");
	}

	@Test
	public void typeSelectionEditedVolume() {
		String selected = Lobid.selectType(Arrays.asList("MultiVolumeBook",
				"BibliographicResource", "EditedVolume"), "type.labels.lobid2");
		assertThat(selected).isEqualTo("EditedVolume");
	}

}
