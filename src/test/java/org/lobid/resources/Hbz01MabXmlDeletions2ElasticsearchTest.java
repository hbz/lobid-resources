/*Copyright 2015,2016,2017 hbz,Pascal Christoph.*Licensed under the Eclipse Public License 1.0*/package org.lobid.resources;

import static org.elasticsearch.common.xcontent.XContentType.JSON;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.morph.Metamorph;
import org.culturegraph.mf.stream.converter.xml.AlephMabXmlHandler;
import org.culturegraph.mf.stream.converter.xml.XmlDecoder;
import org.culturegraph.mf.stream.source.FileOpener;
import org.culturegraph.mf.stream.source.TarReader;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.IndicesAdminClient;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.hp.hpl.jena.rdf.model.Model;

import de.hbz.lobid.helper.CompareJsonMaps;

/***
 * Transform hbz01 Aleph Mab XML catalog deletions into lobid
 * elasticsearch*JSON-LD.Query the index and test the data by transforming the
 * data into*several JSON-LD files(reflecting the records residing in
 * elasticsearch).**
 * 
 * @author Pascal Christoph(dr0i)
 **/
@SuppressWarnings("javadoc")
public final class Hbz01MabXmlDeletions2ElasticsearchTest {
	private static Node node;
	private static Client client;
	private static final Logger LOG =
			LogManager.getLogger(Hbz01MabXmlDeletions2ElasticsearchTest.class);
	private static final String LOBID_DELETION_RESOURCES =
			"test-deletions-resources-" + LocalDateTime.now().toLocalDate() + "-"
					+ LocalDateTime.now().toLocalTime();
	static final String DIRECTORY_TO_TEST_JSON_FILES =
			Hbz01MabXmlEtlNtriples2Filesystem.PATH_TO_TEST + "jsonld-deletions/";
	static HashSet<String> testFiles = new HashSet<>();
	static boolean testFailed = false;
	private static final String JSONLD_CONTEXT_URI =
			"http://lobid.org/resources/context-deletions.jsonld";
	private static UpdateRequest updateRequest;
	private static BulkRequestBuilder bulkRequest;
	private static final String RDF_TYPE_TO_IDENTIFY_ROOT_ID =
			"http://purl.org/dc/terms/BibliographicResource";

	@BeforeClass
	public static void setup() {
		LOG.info("Testing deletion ...");
		try {
			if (System.getProperty("generateTestData", "true").equals("true")) {
				Files.walk(Paths.get(DIRECTORY_TO_TEST_JSON_FILES))
						.filter(Files::isRegularFile)
						.forEach(fname -> testFiles.add(fname.getFileName().toString()));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		node = new Node(Settings.builder()
				.put(Node.NODE_NAME_SETTING.getKey(),
						"testNodeHbz01MabXmlDeletions2ElasticsearchTest")
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
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().get();
		etl(client);
	}

	/*
	 * ETL stands for extract, transform, load. Extract data from AlephmabXml
	 * clobs, transform into lobid ntriples, transform that into elasticsearch
	 * json-ld, index that into elasticsearch.
	 */
	public static void etl(final Client cl) {
		RdfGraphToJsonLd rdfGraphToJsonLd = new RdfGraphToJsonLd();
		rdfGraphToJsonLd
				.setContextLocationFilname("web/conf/context-deletion.jsonld");
		rdfGraphToJsonLd.setContextUri(JSONLD_CONTEXT_URI);
		rdfGraphToJsonLd.setRdfTypeToIdentifyRootId(RDF_TYPE_TO_IDENTIFY_ROOT_ID);
		JsonLdEtikett jsonLdEtikett = new JsonLdEtikett("deletion-labels",
				"web/conf/context-deletion.jsonld");
		JsonLdItemSplitter2ElasticsearchJsonLd jsonLdItemSplitter2es =
				new JsonLdItemSplitter2ElasticsearchJsonLd("id");
		final FileOpener opener = new FileOpener();
		indexHbz01AlephInternalId(client.admin().indices());
		opener.setReceiver(new TarReader())//
				.setReceiver(new XmlDecoder())//
				.setReceiver(new AlephMabXmlHandler())//
				.setReceiver(
						new Metamorph("src/main/resources/morph-hbz01-deletions.xml"))
				.setReceiver(new PipeEncodeTriples())//
				.setReceiver(rdfGraphToJsonLd)//
				.setReceiver(jsonLdEtikett)//
				.setReceiver(jsonLdItemSplitter2es)
				.setReceiver(getElasticsearchIndexer(cl));
		opener.process(
				new File(Hbz01MabXmlEtlNtriples2Filesystem.TEST_FILENAME_ALEPHXMLCLOBS)
						.getAbsolutePath());
		opener.closeStream();
		client.admin().indices().prepareRefresh().get();
	}

	/*
	 * Creates and indexes a document which is needed for a lookup to get the
	 * HT-id.
	 *
	 */
	private static void indexHbz01AlephInternalId(IndicesAdminClient iac) {
		iac.prepareCreate("hbz01").execute().actionGet();
		updateRequest = new UpdateRequest("hbz01", "dummy", "HT011121014");
		updateRequest.docAsUpsert(true);
		String json = "{\"alephInternalSysnumber\":\"009094002\"}";
		updateRequest.doc(json, JSON);
		bulkRequest = client.prepareBulk();
		bulkRequest.add(updateRequest);
		bulkRequest.execute().actionGet();
		client.admin().indices().prepareRefresh("hbz01").get();
		return;
	}

	@SuppressWarnings("static-method")
	@Test
	public void testJson() {
		LOG.info("Testing getting Json from ES");
		ElasticsearchDocuments.getAsJson();
		if (testFailed)
			throw new AssertionError();
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
		esIndexer.setIndexName(LOBID_DELETION_RESOURCES);
		esIndexer.setIndexAliasSuffix("");
		esIndexer.setUpdateNewestIndex(false);
		esIndexer.setIndexConfig("index-config-deletions.json");
		esIndexer.lookupMabxmlDeletion = true;
		esIndexer.onSetReceiver();
		return esIndexer;
	}

	/*
	 * Tears down the elasticsearch test instance. Also clears any test files
	 * which are not part of the original test files archive.
	 */
	@AfterClass
	public static void down() {
		testFiles.forEach(lobidResource -> {
			LOG.warn(lobidResource
					+ " could not be retrieved from ES. Maybe not part of the source archive at"
					+ " all. Otherwise, increase to 'debug' level and search for ES 'Exception'.");
			findTestFiles(lobidResource).forEach(fname -> deleteTestFile(fname));
		});
		client.admin().indices().prepareDelete("_all").execute().actionGet();
		try {
			node.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static List<Path> findTestFiles(String fname) {
		List<Path> pathList = null;
		try {
			pathList = Files
					.find(
							Paths.get(Hbz01MabXmlEtlNtriples2Filesystem.PATH_TO_TEST
									+ "/jsonld-deletions"),
					100,
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

	static class ElasticsearchDocuments {
		static private SearchResponse getElasticsearchDocuments() {
			return client.prepareSearch(LOBID_DELETION_RESOURCES)
					.setQuery(new MatchAllQueryBuilder()).setFrom(0).setSize(10000)
					.execute().actionGet();
		}

		static String getAsNtriples() {
			return Arrays.asList(getElasticsearchDocuments().getHits().getHits())
					.parallelStream().map(hit -> toRdf(hit.getSourceAsString()))
					.collect(Collectors.joining());
		}

		private static void getAsJson() {
			for (SearchHit hit : getElasticsearchDocuments().getHits().getHits()) {
				stripContextAndSaveAsFile(hit.getSourceAsString());
			}
		}

		/*
		 * As the 'context' is just bloating the content the context is stripped
		 * from it. Also, set value of "deleted" to a dummy value because that value
		 * changes dynamically and thus cannot be tested.
		 */
		private static void stripContextAndSaveAsFile(final String jsonLd) {
			String jsonLdWithoutContext = null;
			Map<String, Object> map;
			String filename = null;
			ObjectMapper mapper = new ObjectMapper();
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			String jsonLd2 = jsonLd.replaceFirst("\"deleted\" ?: ?\"[0-9]{8}\"",
					"\"deleted\":\"test-dummy\"");
			try {
				map = mapper.readValue(jsonLd2, Map.class);
				map.put("@context", JSONLD_CONTEXT_URI);
				jsonLdWithoutContext = mapper.writeValueAsString(map);
				filename = ((String) map.get("id"))
						.replaceAll("http://lobid.org/" + ".*/", "").replaceAll("#!$", "");
				testFiles.remove(filename);
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
				final Object jsonObject = JsonUtils.fromString(jsonLd);
				JsonLdOptions options = new JsonLdOptions();
				final Model model = (Model) JsonLdProcessor.toRDF(jsonObject, options);
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
