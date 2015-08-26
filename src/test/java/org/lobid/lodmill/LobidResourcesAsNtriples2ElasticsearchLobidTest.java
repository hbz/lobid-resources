/* Copyright 2015  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.lobid.lodmill;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

/**
 * Read a directory with records got from lobid.org in ntriple serialization.
 * The records are indexed as JSON-LD in an in-memory elasticsearch instance,
 * then queried and concatenated into one file. This file is compared to the
 * input records. Test is ok if ntriples are equal.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class LobidResourcesAsNtriples2ElasticsearchLobidTest {

	private static final String TEST_FILENAME = "hbz01.es.nt";

	@SuppressWarnings("static-method")
	@Test
	public void testFlow() throws URISyntaxException {
		String ntriples = getElasticsearchDocumentsAsNtriples();
		File testFile = new File(TEST_FILENAME);
		try {
			FileUtils.writeStringToFile(testFile, ntriples, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
		AbstractIngestTests.compareFilesDefaultingBNodes(testFile,
				new File(Thread.currentThread().getContextClassLoader().getResource(TEST_FILENAME).toURI()));
		testFile.deleteOnExit();
	}

	private static String getElasticsearchDocumentsAsNtriples() {
		return "";
	}
}
