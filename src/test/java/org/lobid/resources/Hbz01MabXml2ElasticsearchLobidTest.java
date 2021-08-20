/* Copyright 2015-2017 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package org.lobid.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.lobid.resources.run.MabXml2lobidJsonEs;
import org.lobid.resources.run.WikidataGeodata2Es;
import org.metafacture.biblio.AlephMabXmlHandler;
import org.metafacture.flowcontrol.ObjectThreader;
import org.metafacture.io.FileOpener;
import org.metafacture.io.RecordReader;
import org.metafacture.io.TarReader;
import org.metafacture.metamorph.Metamorph;
import org.metafacture.strings.StringReader;
import org.metafacture.xml.XmlDecoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import de.hbz.lobid.helper.CompareJsonMaps;

/**
 * Transform hbz01 Aleph Mab XML catalog data into lobid elasticsearch JSON-LD.
 * Query the index and test the data by transforming the data into one big
 * ntriple file (which is great to make diffs) and into several JSON-LD files
 * (reflecting the records residing in elasticsearch).
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class Hbz01MabXml2ElasticsearchLobidTest {
	private static Node node;
	static Client client;
	private static final Logger LOG =
			LogManager.getLogger(Hbz01MabXml2ElasticsearchLobidTest.class);
	private static final String LOBID_RESOURCES =
			"test-resources-" + LocalDateTime.now().toLocalDate() + "-"
					+ LocalDateTime.now().toLocalTime();
	private static final String TEST_FILENAME_NTRIPLES =
			Hbz01MabXmlEtlNtriples2Filesystem.PATH_TO_TEST + "hbz01.es.nt";
	private static final String DIRECTORY_TO_TEST_JSON_FILES =
			Hbz01MabXmlEtlNtriples2Filesystem.PATH_TO_TEST + "jsonld/";
	private static HashSet<String> testFiles = new HashSet<>();
	private static boolean testFailed = false;
	private static RdfGraphToJsonLd rdfGraphToJsonLd;

	@BeforeClass
	public static void setup() {
		try {
			if (System.getProperty("generateTestData", "false").equals("true")) {
				Files.walk(Paths.get(DIRECTORY_TO_TEST_JSON_FILES))
						.filter(Files::isRegularFile)
						.forEach(fname -> testFiles.add(fname.getFileName().toString()));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		node = new Node(Settings.builder()
				.put(Node.NODE_NAME_SETTING.getKey(),
						"testNodeHbz01MabXml2ElasticsearchLobidTest")
				.put(NetworkModule.TRANSPORT_TYPE_KEY, NetworkModule.LOCAL_TRANSPORT)
				.put(NetworkModule.HTTP_ENABLED.getKey(), false) //
				.put(Environment.PATH_HOME_SETTING.getKey(), "tmp")//
				.build());
		try {
			node.start();
		} catch (NodeValidationException e) {
			e.printStackTrace();
		}
		client = node.client();
		client.admin().indices().prepareDelete("_all").execute().actionGet();
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute()
				.actionGet();
		// load wikidata geo coordinates into es
		WikidataGeodata2Es.esIndexer
				.setIndexName(WikidataGeodata2Es.getIndexAlias());
		WikidataGeodata2Es.setElasticsearchIndexer(client);
		WikidataGeodata2Es.filterWikidataEntitiesDump2EsGeodata(
				"src/test/resources/wikidataEntities.json");
		WikidataGeodata2Es.finish();
		etl();
	}

	/*
	 * ETL stands for extract, transform, load. Extract data from AlephmabXml
	 * clobs, transform into lobid ntriples, transform that into elasticsearch
	 * json-ld, index that into elasticsearch.
	 */
	static void etl() {
		final FileOpener opener = new FileOpener();
		rdfGraphToJsonLd = new RdfGraphToJsonLd(MabXml2lobidJsonEs.CONTEXT_URI);
		opener.setReceiver(new TarReader()).setReceiver(new RecordReader())
				.setReceiver(new ObjectThreader<String>())//
				.addReceiver(receiverThread())//
				.addReceiver(receiverThread());
		opener.process(
				new File(Hbz01MabXmlEtlNtriples2Filesystem.TEST_FILENAME_ALEPHXMLCLOBS)
						.getAbsolutePath());
		try {
			opener.closeStream();
		} catch (NullPointerException e) {
			// ignore, see https://github.com/hbz/lobid-resources/issues/1030
		}

	}

	private static StringReader receiverThread() {
		StringReader sr = new StringReader();
		sr.setReceiver(new XmlDecoder()).setReceiver(new AlephMabXmlHandler())
				.setReceiver(
						new Metamorph("src/main/resources/morph-hbz01-to-lobid.xml"))
				.setReceiver(new PipeEncodeTriples())//
				.setReceiver(new RdfGraphToJsonLd(MabXml2lobidJsonEs.CONTEXT_URI))//
				.setReceiver(new JsonLdEtikett())//
				.setReceiver(new JsonLdItemSplitter2ElasticsearchJsonLd("hbzId"))//
				.setReceiver(getElasticsearchIndexer(client));
		return sr;
	}

	@SuppressWarnings("static-method")
	@Test
	public void testJson() {
		LOG.info("Testing getting Json from ES");
		ElasticsearchDocuments.getAsJson();
		if (testFailed)
			throw new AssertionError();
	}

	@SuppressWarnings("static-method")
	@Test
	public void testNtriples() {
		LOG.info("Testing getting Json from ES and transform that to ntriples");
		getElasticsearchDocsAsNtriplesAndTestAndWrite();
	}

	static void getElasticsearchDocsAsNtriplesAndTestAndWrite() {
		SortedSet<String> set =
				getSortedSet(ElasticsearchDocuments.getAsNtriples());
		try {
			AbstractIngestTests.compareSetAndFileDefaultingBNodesAndCommata(set,
					new File(TEST_FILENAME_NTRIPLES));
		} finally {
			writeSetToFile(TEST_FILENAME_NTRIPLES, set);
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
		esIndexer.setIndexConfig("index-config.json");
		esIndexer.lookupWikidata = true;
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
		testFiles.forEach(lobidResource -> {
			LOG.warn(lobidResource
					+ " could not be retrieved from ES. Maybe not part of the source archive at"
					+ " all. Otherwise, increase to 'debug' level and search for ES 'Exception'.");
			findTestFiles(lobidResource).forEach(fname -> deleteTestFile(fname));
		});
	}

	private static List<Path> findTestFiles(String fname) {
		List<Path> pathList = null;
		try {
			pathList = Files
					.find(Paths.get(Hbz01MabXmlEtlNtriples2Filesystem.PATH_TO_TEST), 100,
							(name, t) -> name.toFile().getName()
									.matches(".*" + fname + "\\.?(json)?(nt)?"))
					.collect(Collectors.toList());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return pathList;
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
					.map(hit -> AbstractIngestTests.toRdf(
							cleanseEndtime(hit.getSourceAsString()),
							"http://lobid.org/resources/context.jsonld",
							rdfGraphToJsonLd.getContextLocationFilename()))
					.collect(Collectors.joining());
		}

		private static void getAsJson() {
			for (SearchHit hit : getElasticsearchDocuments().getHits().getHits()) {
				saveAsFile(cleanseEndtime(hit.getSourceAsString()));
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
		private static void saveAsFile(final String jsonLd) {
			Map<String, Object> map;
			String filename = null;
			ObjectMapper mapper = new ObjectMapper();
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			try {
				map = mapper.readValue(jsonLd, Map.class);
				filename = ((String) map.get("id"))
						.replaceAll("http://lobid.org/" + ".*/", "").replaceAll("#!$", "");
				testFiles.remove(filename);
				if (filename.contains(":"))
				filename="items/"+filename;
				filename = DIRECTORY_TO_TEST_JSON_FILES + filename+".json";
				if (!new File(filename).exists()) 
					writeFile(filename, mapper.writeValueAsString(map));
				else {
					try (FileInputStream fis = new FileInputStream(filename)) {
						Map<String, Object> jsonMap =
								new ObjectMapper().readValue(fis, Map.class);
						boolean same = new CompareJsonMaps().writeFileAndTestJson(
								new ObjectMapper().convertValue(jsonMap, JsonNode.class),
								new ObjectMapper().convertValue(map, JsonNode.class));
						if (!same) {
							writeFile(filename, mapper.writeValueAsString(map));
							testFailed = true;
						}
					}
				}
			} catch (IOException e) {
				LOG.error("Errored computing " + filename);
				deleteTestFile(Paths.get(filename));
			}
		}
	}
}
