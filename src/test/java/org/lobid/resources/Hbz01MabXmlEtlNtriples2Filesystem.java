/* Copyright 2017  hbz, Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.xml.AlephMabXmlHandler;
import org.culturegraph.mf.stream.converter.xml.XmlDecoder;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.stream.source.TarReader;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Transform hbz01 Aleph Mab XML catalog records into ntriples files.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
public final class Hbz01MabXmlEtlNtriples2Filesystem {
	public static final String N_TRIPLE = "N-TRIPLE";
	static final String PATH_TO_TEST = "src/test/resources/";
	static final String TEST_FILENAME_ALEPHXMLCLOBS =
			PATH_TO_TEST + "hbz01XmlClobs.tar.bz2";
	static boolean testFailed = false;
	static final String NTRIPLES_DEBUG_FILES = "src/test/resources/input/nt";
	private static final Logger LOG =
			LogManager.getLogger(Hbz01MabXmlEtlNtriples2Filesystem.class);

	/**
	 * Clean directory from old test files when property is set.
	 */
	@BeforeClass
	public static void removeTestFiles() {
		try {
			deleteFilesRecursively("input");
			if (System.getProperty("generateTestData", "false").equals("true")) {
				deleteFilesRecursively("jsonld");
				deleteFilesRecursively("jsonld-deletions");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void deleteFilesRecursively(final String DIRECTORY)
			throws IOException {
		LOG.info("Tabula rasa: cleaning test directory '" + DIRECTORY + "'");
		try (Stream<Path> files = Files.find(
				Paths.get(Hbz01MabXmlEtlNtriples2Filesystem.PATH_TO_TEST + DIRECTORY),
				100, (path, t) -> path.toFile().isFile())) {
			files.filter(Files::isRegularFile).map(Path::toFile)
					.forEach(File::delete);
		}
	}

	/**
	 * ETL stands for extract, transform, load. Extract data from AlephmabXml
	 * clobs, transform into lobid ntriples defaulting the "endTime" triples (for
	 * test purpose) and load this into the filesystem. The files are used as
	 * input for the @see de.hbz.lobid.helper.JsonConverter .
	 */
	@SuppressWarnings("static-method")
	@Test
	public void etlOutputAsNtriples2Filesystem() {
		final FileOpener opener = new FileOpener();
		final Triples2RdfModel triple2model = new Triples2RdfModel();
		RdfModelFileWriter rdfModelFileWriter = new RdfModelFileWriter();
		rdfModelFileWriter.setProperty("http://purl.org/lobid/lv#hbzID");
		rdfModelFileWriter.setStartIndex(2);
		rdfModelFileWriter.setEndIndex(7);
		rdfModelFileWriter.setTarget(NTRIPLES_DEBUG_FILES);
		triple2model.setInput(N_TRIPLE);
		opener.setReceiver(new TarReader()).setReceiver(new XmlDecoder())
				.setReceiver(new AlephMabXmlHandler())
				.setReceiver(
						new Metamorph("src/main/resources/morph-hbz01-to-lobid.xml"))
				.setReceiver(new PipeEncodeTriples())
				.setReceiver(new SimpleStringSubstituter(
						"endTime> \"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\"",
						"endTime> \"0001-01-01T00:00:00\""))
				.setReceiver(triple2model).setReceiver(rdfModelFileWriter);
		opener.process(new File(TEST_FILENAME_ALEPHXMLCLOBS).getAbsolutePath());
		opener.closeStream();
	}
}
