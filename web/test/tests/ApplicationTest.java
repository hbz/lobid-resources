/* Copyright 2014-2016 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package tests;

import static org.fest.assertions.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Test;

import controllers.resources.Lobid;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaTest
 */
@SuppressWarnings("javadoc")
public class ApplicationTest {

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

}
