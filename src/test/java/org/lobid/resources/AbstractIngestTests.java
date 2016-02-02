/* Copyright 2013 Fabian Steeg. Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;

import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.morph.MorphErrorHandler;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for executing tests.
 * 
 * @author Fabian Steeg (fsteeg)
 * @author Pascal Christoph (dr0i)
 */
@SuppressWarnings("javadoc")
public abstract class AbstractIngestTests {

	static final String LOBID_JSONLD_CONTEXT =
			"http://lobid.org/download/contextTmp.json";
	private static final Logger LOG =
			LoggerFactory.getLogger(AbstractIngestTests.class);

	protected Metamorph metamorph;

	private static SortedSet<String> linesInFileToSetDefaultingBNodesAndCommata(
			final File file) {
		SortedSet<String> set = null;
		try (Scanner scanner = new Scanner(file)) {
			set = asDefaultSet(scanner);
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
		return set;
	}

	/**
	 * Tests if two files are of equal content. As BNodes are not fix they are not
	 * comparable and thus they are defaulted to "_:bnodeDummy" to make the
	 * ntriple files comparable anyhow. For the same reason the comma at the end
	 * of a line is removed to be able to compare json files.
	 * 
	 * @param generatedFile the actually generated file
	 * @param testFile the file which defines how the generatedFile should look
	 *          like
	 */
	public static void compareFilesDefaultingBNodesAndCommata(
			final File generatedFile, final File testFile) {
		assertSetSize(linesInFileToSetDefaultingBNodesAndCommata(testFile),
				linesInFileToSetDefaultingBNodesAndCommata(generatedFile));
		assertSetElements(linesInFileToSetDefaultingBNodesAndCommata(generatedFile),
				linesInFileToSetDefaultingBNodesAndCommata(testFile));
	}

	private static void assertSetSize(final SortedSet<String> expectedSet,
			final SortedSet<String> actualSet) {
		if (expectedSet.size() != actualSet.size()) {
			LOG.info("expectedSet:");
			for (String s : expectedSet) {
				LOG.info(s);
			}
			System.out.println();
			LOG.info("actualSet:");
			for (String s : actualSet) {
				LOG.info(s);
			}
			final SortedSet<String> missingSet = new TreeSet<>(expectedSet);
			missingSet.removeAll(actualSet);
			LOG.error("Missing expected result set entries: " + missingSet);
		}
		Assert.assertEquals(expectedSet.size(), actualSet.size());
	}

	private static SortedSet<String> asDefaultSet(final Scanner scanner) {
		final SortedSet<String> set = new TreeSet<>();
		while (scanner.hasNextLine()) {
			String actual = scanner.nextLine();
			if (!actual.isEmpty()) {
				actual =
						actual.replaceFirst("(^_:\\w* )|( _:\\w* ?.$)", "_:bnodeDummy ");
				actual = actual.replaceFirst(",$", "");
				set.add(actual);
			}
		}
		return set;
	}

	private static void assertSetElements(final SortedSet<String> expectedSet,
			final SortedSet<String> actualSet) {
		final Iterator<String> expectedIterator = expectedSet.iterator();
		final Iterator<String> actualIterator = actualSet.iterator();
		for (int i = 0; i < expectedSet.size(); i++) {
			String expected = expectedIterator.next();
			String actual = actualIterator.next();
			if (!expected.equals(actual)) {
				LOG.error("Expected: " + expected + "\n but was:" + actual);
			}
		}
		Assert.assertEquals(expectedSet, actualSet);
	}

	protected static void setUpErrorHandler(final Metamorph metamorph) {
		metamorph.setErrorHandler(new MorphErrorHandler() {
			@Override
			public void error(final Exception exception) {
				LOG.error(exception.getMessage(), exception);
			}
		});
	}

}
