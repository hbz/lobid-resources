/* Copyright 2013 Fabian Steeg. Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.morph.MorphErrorHandler;
import org.junit.Assert;

import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.impl.NQuadTripleCallback;
import com.github.jsonldjava.utils.JsonUtils;

/**
 * Helper class for executing tests.
 * 
 * @author Fabian Steeg (fsteeg)
 * @author Pascal Christoph (dr0i)
 */
@SuppressWarnings("javadoc")
public abstract class AbstractIngestTests {

	private static final Logger LOG =
			LogManager.getLogger(AbstractIngestTests.class);

	protected Metamorph metamorph;

	@SuppressWarnings("resource")
	private static Stream<String> fileToStream(final File file) {
		Stream<String> stream = null;
		InputStream is;
		try {
			is = new FileInputStream(file);
			stream =
					new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
							.lines();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return stream;
	}

	/**
	 * Calls @see{compareStreamsDefaultingBNodesAndCommata}.
	 * 
	 * @param generatedSet the generated data as set
	 * @param testFile expected data as file
	 */
	public static void compareSetAndFileDefaultingBNodesAndCommata(
			final SortedSet<String> generatedSet, final File testFile) {
		compareStreamsDefaultingBNodesAndCommata(generatedSet.stream(),
				fileToStream(testFile));
	}

	/**
	 * Calls @see{compareStreamsDefaultingBNodesAndCommata}.
	 * 
	 * @param generatedFile the generated data as file
	 * @param testFile expected data as file
	 */
	public static void compareFilesDefaultingBNodesAndCommata(
			final File generatedFile, final File testFile) {
		compareStreamsDefaultingBNodesAndCommata(fileToStream(generatedFile),
				fileToStream(testFile));
	}

	/**
	 * Tests if two Streams are of equal content. As BNodes are not fix they are
	 * not comparable and thus they are defaulted to "_:bnodeDummy" to make the
	 * ntriple files comparable anyhow. For the same reason the comma at the end
	 * of a line is removed to be able to compare json files.
	 * 
	 * @param generated the generated data
	 * @param expected the expected data
	 */
	public static void compareStreamsDefaultingBNodesAndCommata(
			final Stream<String> generated, final Stream<String> expected) {
		SortedSet<String> expectedSet = asNormalizedSet(expected);
		SortedSet<String> generatedSet = asNormalizedSet(generated);
		assertSetSize(expectedSet, generatedSet);
		assertSetElements(expectedSet, generatedSet);
	}

	private static void assertSetSize(final SortedSet<String> expectedSet,
			final SortedSet<String> actualSet) {
		if (expectedSet.size() != actualSet.size()) {
			LOG.debug("expectedSet:");
			for (String s : expectedSet) {
				LOG.debug(s);
			}
			LOG.debug("actualSet:");
			for (String s : actualSet) {
				LOG.debug(s);
			}
			final SortedSet<String> missingSet = new TreeSet<>(expectedSet);
			missingSet.removeAll(actualSet);
			LOG.error(
					"Missing expected result set entries (showing first 2048 bytes): "
							+ (missingSet.toString().length() > 2048
									? missingSet.toString().substring(0, 2048)
									: missingSet.toString()));
		}
		Assert.assertEquals(expectedSet.size(), actualSet.size());
	}

	private static SortedSet<String> asNormalizedSet(
			Stream<String> unnormalizedStream) {
		return new TreeSet<>(unnormalizedStream.flatMap(s -> Stream
				.of(s.replaceFirst("(^_:\\w*)|( _:\\w* ?.$)", "_:bnodeDummy ")
						.replaceFirst(",$", "")))
				.collect(Collectors.toList()));
	}

	private static void assertSetElements(final SortedSet<String> expectedSet,
			final SortedSet<String> actualSet) {
		final Iterator<String> expectedIterator = expectedSet.iterator();
		final Iterator<String> actualIterator = actualSet.iterator();
		StringBuilder message = new StringBuilder("\n");
		boolean failed = false;
		for (int i = 0; i < expectedSet.size(); i++) {
			String expected = expectedIterator.next();
			String actual = actualIterator.next();
			if (expected.endsWith("<http://www.w3.org/2001/XMLSchema#double> .")) {
				expected = expected.replace(",", ".");
				actual = actual.replace(",", ".");
			}
			if (!expected.equals(actual)) {
				failed = true;
				message.append("\nExpected:" + expected + "\nbut was :" + actual);
			}
		}
		if (failed)
			LOG.error(message.toString() + "\n");
		Assert.assertFalse("Data not as expected!", failed);
	}

	protected static void setUpErrorHandler(final Metamorph metamorph) {
		metamorph.setErrorHandler(new MorphErrorHandler() {
			@Override
			public void error(final Exception exception) {
				LOG.error(exception.getMessage(), exception);
			}
		});
	}

	public static String toRdf(final String jsonLd, String contextUrl,
			String contextLocation) {
		try {
			LOG.info("toRdf: " + jsonLd);
			String context =
					new String(Files.readAllBytes(Paths.get(contextLocation)));
			String jsonWithLocalContext =
					jsonLd.replaceFirst("\\{\"@context\":\"" + contextUrl + "\"",
							context.substring(0, context.length() - 2));
			final Object jsonObject = JsonUtils.fromString(jsonWithLocalContext);
			NQuadTripleCallback nqtc = new NQuadTripleCallback();
			JsonLdOptions options = new JsonLdOptions();
			Object obj = JsonLdProcessor.toRDF(jsonObject, nqtc, options);
			return obj.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
