/* Copyright 2015,2016,2017  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.lobid.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.stream.source.FileOpener;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.search.SearchHit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lobid.resources.run.LocBibframe2JsonEs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaTripleCallback;
import com.github.jsonldjava.utils.JSONUtils;
import com.hp.hpl.jena.rdf.model.Model;

import de.hbz.lobid.helper.CompareJsonMaps;

/**
 * Transform loc bibframe instances into JSON-LD and index that into
 * elasticsearch. Query the index and test the data by transforming the data
 * into one big ntriple file (which is great to make diffs) and into several
 * JSON-LD files (reflecting the records residing in elasticsearch).
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class LocBibframeInstances2ElasticsearchTest {
	private static Node node;
	private static Client client;
	private static final Logger LOG =
			LogManager.getLogger(LocBibframeInstances2ElasticsearchTest.class);
	private static final String LOBID_RESOURCES =
			"test-loc-" + LocalDateTime.now().toLocalDate() + "-"
					+ LocalDateTime.now().toLocalTime();
	private static final String INPUT_FN_NTRIPLES =
			Hbz01MabXmlEtlNtriples2Filesystem.PATH_TO_TEST + "loc_bib_works_test.nt";
	private static final String OUTPUT_FN_NTRIPLES =
			INPUT_FN_NTRIPLES + ".output.nt";
	private static final String DIRECTORY_TO_TEST_JSON_FILES =
			Hbz01MabXmlEtlNtriples2Filesystem.PATH_TO_TEST + "jsonld-loc/";
	private static boolean testFailed = false;
	private final static RdfModel2ElasticsearchEtikettJsonLd rdfModel2ElasticsearchEtikettJsonLd =
			new RdfModel2ElasticsearchEtikettJsonLd(
					new File(LocBibframe2JsonEs.DIRECTORY_TO_LOC_LABELS),
					LocBibframe2JsonEs.LOC_CONTEXT);

	@BeforeClass
	public static void setup() {
		try {
			if (System.getProperty("generateTestData", "false").equals("true")) {
				LOG.info(
						"You set generateTestData=true. Thus, first going to remove all test files residing in "
								+ "the test directory. The build will be mostly 'succesful' and you sould check the changes "
								+ "by oing a 'git diff' on your own.");
				Files.walk(Paths.get(DIRECTORY_TO_TEST_JSON_FILES))
						.filter(Files::isRegularFile).map(Path::toFile)
						.forEach(File::delete);

			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		node = new Node(Settings.builder()
				.put(Node.NODE_NAME_SETTING.getKey(),
						"testNodeLocNtriples2ElasticsearchLobidTest")
				.put(NetworkModule.TRANSPORT_TYPE_KEY, NetworkModule.LOCAL_TRANSPORT)
				.put(NetworkModule.HTTP_ENABLED.getKey(), false) //
				.put(Environment.PATH_HOME_SETTING.getKey(), "tmp")//
				.build());
		try {
			node.start();
		} catch (NodeValidationException e) {
			e.printStackTrace();
		}
		rdfModel2ElasticsearchEtikettJsonLd
				.setContextLocation("web/conf/context-loc.jsonld");
		client = node.client();
		client.admin().indices().prepareDelete("_all").execute().actionGet();
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute()
				.actionGet();
		etl(client, rdfModel2ElasticsearchEtikettJsonLd);
	}

	/*
	 * ETL stands for extract, transform, load. Extract data from AlephmabXml
	 * clobs, transform into lobid ntriples, transform that into elasticsearch
	 * json-ld, index that into elasticsearch.
	 */
	static void etl(final Client cl,
			RdfModel2ElasticsearchEtikettJsonLd etikettJsonLdConverter) {
		etikettJsonLdConverter
				.setIdPatternMainNode(LocBibframe2JsonEs.ROOT_SUBJECT_PATTERN);
		etikettJsonLdConverter
				.setRootIdPredicate(LocBibframe2JsonEs.ROOT_ID_PREDICATE);
		final FileOpener opener = new FileOpener();
		final StringRecordSplitter srs =
				new StringRecordSplitter(LocBibframe2JsonEs.RECORD_SPLITTER_MARKER);
		final Triples2RdfModel triple2model = new Triples2RdfModel();
		triple2model.setInput(Hbz01MabXmlEtlNtriples2Filesystem.N_TRIPLE);
		opener.setReceiver(srs).setReceiver(triple2model)
				.setReceiver(etikettJsonLdConverter)
				.setReceiver(getElasticsearchIndexer(cl));
		opener.process(INPUT_FN_NTRIPLES);
		opener.closeStream();

	}

	@SuppressWarnings("static-method")
	@Test
	public void testJson() {
		LOG.info("Testing getting Json from ES ... ");
		ElasticsearchDocuments.getAsJson();
		if (testFailed)
			throw new AssertionError();
		LOG.info("... finished testing of getting Json from ES");
	}

	@SuppressWarnings("static-method")
	@Test
	public void testNtriples() {
		LOG.info("Testing getting Json from ES and transform that to ntriples");
		getElasticsearchDocsAsNtriplesAndTestAndWrite();
		LOG.info(
				"... finished testing of getting Json from ES and transform that to ntriples");

	}

	static void getElasticsearchDocsAsNtriplesAndTestAndWrite() {
		SortedSet<String> set =
				getSortedSet(ElasticsearchDocuments.getAsNtriples());
		try {
			AbstractIngestTests.compareSetAndFileDefaultingBNodesAndCommata(set,
					new File(OUTPUT_FN_NTRIPLES));
		} finally {
			writeSetToFile(OUTPUT_FN_NTRIPLES, set);
		}
	}

	private static SortedSet<String> getSortedSet(final String DOCUMENT) {
		SortedSet<String> set = new TreeSet<>();
		try (Scanner scanner = new Scanner(DOCUMENT)) {
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				if (!line.isEmpty()) {
					line = line.replaceAll("(_:\\w*)", "_:bnodeDummy").replaceFirst(",$",
							"");
					set.add(line);
				}
			}
		}
		return set;
	}

	private static void writeSetToFile(final String TEST_FILENAME,
			SortedSet<String> set) {
		File testFile = new File(TEST_FILENAME);
		try {
			FileUtils.writeStringToFile(testFile,
					set.parallelStream().collect(Collectors.joining("\n")), false);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void writeFile(final String TEST_FILENAME,
			final String DOCUMENT) {
		File testFile = new File(TEST_FILENAME);
		LOG.info("Write " + TEST_FILENAME);
		try {
			FileUtils.writeStringToFile(testFile, DOCUMENT, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static ElasticsearchIndexer getElasticsearchIndexer(final Client cl) {
		ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
		esIndexer.setElasticsearchClient(cl);
		esIndexer.setIndexName(LOBID_RESOURCES);
		esIndexer.setIndexAliasSuffix("");
		esIndexer.setUpdateNewestIndex(false);
		esIndexer.setIndexConfig(LocBibframe2JsonEs.INDEX_CONFIG_BIBFRAME);
		esIndexer.lookupWikidata = false;
		esIndexer.onSetReceiver();
		return esIndexer;
	}

	/*
	 * Tears down the elasticsearch test instance. Also clears any test files
	 * which are not part of the original test files archive.
	 */
	@AfterClass
	public static void down() {
		client.admin().indices().prepareDelete("_all").execute().actionGet();
		try {
			node.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void deleteTestFile(Path fname) {
		try {
			LOG.info("Removing test file " + fname);
			Files.delete(fname);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class ElasticsearchDocuments {
		static private SearchResponse getElasticsearchDocuments() {
			return client.prepareSearch(LOBID_RESOURCES)
					.setQuery(new MatchAllQueryBuilder()).setFrom(0).setSize(10000)
					.execute().actionGet();
		}

		private static String getAsNtriples() {
			return Arrays.asList(getElasticsearchDocuments().getHits().getHits())
					.parallelStream()
					.map(hit -> toRdf(cleanseEndtime(hit.getSourceAsString())))
					.collect(Collectors.joining());
		}

		private static void getAsJson() {
			for (SearchHit hit : getElasticsearchDocuments().getHits().getHits()) {
				stripContextAndSaveAsFile(cleanseEndtime(hit.getSourceAsString()));
			}
		}

		private static String cleanseEndtime(String jsonld) {
			return jsonld.replaceFirst(
					"\"endTime\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\"",
					"\"endTime\":\"0001-01-01T00:00:00\"");
		}

		/*
		 * As the 'context' is just bloating the content the context is stripped
		 * from it.
		 */
		private static void stripContextAndSaveAsFile(final String jsonLd) {
			String jsonLdWithoutContext = null;
			Map<String, Object> map;
			String filename = null;
			ObjectMapper mapper = new ObjectMapper();
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			try {
				map = mapper.readValue(jsonLd, Map.class);
				map.put("@context", LocBibframe2JsonEs.LOC_CONTEXT);
				jsonLdWithoutContext = mapper.writeValueAsString(map);
				filename = ((String) map.get("id")).replaceAll(".*/", "")
						.replaceAll("#!$", "");
				filename = DIRECTORY_TO_TEST_JSON_FILES + filename;
				if (!new File(filename).exists())
					writeFile(filename, jsonLdWithoutContext);
				else {
					try (FileInputStream fis = new FileInputStream(filename)) {
						Map<String, Object> jsonMap =
								new ObjectMapper().readValue(fis, Map.class);
						boolean same = new CompareJsonMaps().writeFileAndTestJson(
								new ObjectMapper().convertValue(jsonMap, JsonNode.class),
								new ObjectMapper().convertValue(map, JsonNode.class));
						if (!same) {
							writeFile(filename, jsonLdWithoutContext);
							testFailed = true;
						}
					}
				}
			} catch (IOException e) {
				LOG.error("Errored computing " + filename);
				deleteTestFile(Paths.get(filename));
			}
		}

		private static String toRdf(final String jsonLd) {
			try {
				LOG.trace("toRdf: " + jsonLd);
				String jsonWithLocalContext = jsonLd.replaceFirst(
						"@context\" ?: ?\"" + LocBibframe2JsonEs.LOC_CONTEXT + "\"",
						"@context\":\"" + new File(
								rdfModel2ElasticsearchEtikettJsonLd.getContextLocation())
										.toURI().toString()
								+ "\"");

				final Object jsonObject = JSONUtils.fromString(jsonWithLocalContext);
				final JenaTripleCallback callback = new JenaTripleCallback();
				final Model model = (Model) JsonLdProcessor.toRDF(jsonObject, callback);
				final StringWriter writer = new StringWriter();
				model.write(writer, Hbz01MabXmlEtlNtriples2Filesystem.N_TRIPLE);
				return writer.toString();
			} catch (IOException | JsonLdError e) {
				e.printStackTrace();
			}
			return null;
		}
	}
}
