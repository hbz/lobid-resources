package org.lobid.resources.run;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.elasticsearch.client.Client;
import org.lobid.resources.ElasticsearchIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gdata.util.common.base.Pair;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

/**
 * Indexing wikidata geo data into our geo-enrichment service. Gets a set of
 * Wikidata Entities via a SPARQL query. These entities are looked up, mapped to
 * lobid-geo-data json and indexed into an elastisearch instance to be easily
 * consumable by lobid. This is done for caching reasons, in particular to gain
 * performance as these documents are stored in our own fast elasticsearch
 * instance. The harvesting, transforming and indexing is deliberately done as a
 * standalone application. Thus, our other applications can geo-enrich their
 * data by querying our fast elasticsearch index. So, no bottleneck when e.g.
 * wikidata slows down querying.
 * 
 * @author Fabian Steeg (fsteeg)
 * @author Pascal Christoph (dr0i)
 */
public class WikidataEntityGeodata2Es {

	private static final String JSON = "application/json";
	static ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
	private static final String DATE =
			new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
	private static final Logger LOG =
			LoggerFactory.getLogger(WikidataEntityGeodata2Es.class);
	static JsonNode wdEntitiesJson = null;
	static String sparql_query;
	private static String indexAlias = null;

	/**
	 * @param args ignored
	 * @throws IOException problems with the sparql query file
	 * @throws UnsupportedEncodingException problems with the sparql query file
	 */
	public static void main(String... args)
			throws UnsupportedEncodingException, IOException {
		sparql_query = new String(
				Files.readAllBytes(Paths.get(
						"src/main/resources/getNwbibSubjectLocationsAsWikidataEntities.txt")),
				"UTF-8");
		esIndexer.setClustername("gaia-aither");
		esIndexer.setHostname("gaia.hbz-nrw.de");
		setElasticsearchIndexer();
		LOG.info("Going to index");
		setWikidatEntitiesJsonDefaultLoadedFromFile();
		start();
		esIndexer.onCloseStream();
	}

	/**
	 * Sets json default file listing all wikidata entities which will be looked
	 * uped later.
	 */
	public static void setWikidatEntitiesJsonDefaultLoadedFromFile() {
		try {
			wdEntitiesJson = new ObjectMapper()
					.readTree((new File("src/test/resources/wikidataEntities.json")));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Initialize the class with a given client.
	 * 
	 * @param client the elasticsearch client
	 */
	public static void setElasticsearchIndexer(Client client) {
		esIndexer.setElasticsearchClient(client);
		setElasticsearchIndexer();
	}

	private static void setElasticsearchIndexer() {
		final String indexNamePrefix = "tmp";
		final String indexAliasSuffix = "genwbi";
		setIndexAlias(indexNamePrefix + indexAliasSuffix);
		esIndexer.setIndexName(indexNamePrefix + "-" + DATE);
		esIndexer.setIndexAliasSuffix(indexAliasSuffix);
		esIndexer.setUpdateNewestIndex(false);
		esIndexer.setIndexConfig("index-config-wd-geodata.json");
		esIndexer.onSetReceiver();
	}

	private static JsonNode toApiResponse(AsyncHttpClient client, String api) {
		try {
			LOG.debug("Sparql query=" + api);
			Response response = client.prepareGet(api).setHeader("Accept", JSON)
					.setFollowRedirects(true).execute().get();
			return new ObjectMapper().readValue(response.getResponseBodyAsStream(),
					JsonNode.class);
		} catch (ExecutionException | InterruptedException | IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Starts getting the wikidata entities and load them into elasticsearch.
	 */
	public static void start() {
		try (AsyncHttpClient client = new AsyncHttpClient()) {
			JsonNode jnode = wdEntitiesJson == null
					? toApiResponse(client, sparql_query) : wdEntitiesJson;
			stream(jnode.get("results").get("bindings"))
					.map(node -> toApiResponse(client,
							node.get("item").get("value").textValue()))
					.map(transform2lobidWikidata()) //
					.forEach(index2Es());
		} catch (Exception e) {
			LOG.error("Can't get wikidata entities ", e);
		}
	}

	/**
	 * Finish loading data into elasticsearch.
	 */
	public static void finish() {
		esIndexer.updateAliases();
		esIndexer.onCloseStream();
	}

	private static Consumer<Pair<String, JsonNode>> index2Es() {
		return idAndJson -> {
			HashMap<String, String> jsonMap = new HashMap<>();
			jsonMap.put(ElasticsearchIndexer.Properties.TYPE.getName(),
					"wikidata-geo");
			jsonMap.put(ElasticsearchIndexer.Properties.GRAPH.getName(),
					idAndJson.second.toString());
			jsonMap.put(ElasticsearchIndexer.Properties.ID.getName(),
					idAndJson.first);
			esIndexer.process(jsonMap);
		};
	}

	private static Function<JsonNode, Pair<String, JsonNode>> transform2lobidWikidata() {
		return node -> {
			JsonNode geoNode = node.findPath("P625").findPath("mainsnak")
					.findPath("datavalue").findPath("value");
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode root = mapper.createObjectNode();
			ObjectNode spatial = mapper.createObjectNode();
			ObjectNode geo = mapper.createObjectNode();
			root.set("spatial", spatial);
			JsonNode aliasNode = node.findPath("aliases").findPath("de");
			if (!aliasNode.isMissingNode())
				root.set("alias", aliasNode);
			spatial.put("type", "Location");
			spatial.put("id",
					"http://www.wikidata.org/entity/" + node.findPath("id").asText());
			spatial.put("label",
					node.findPath("labels").findPath("de").findPath("value").asText());
			spatial.set("geo", geo);
			geo.put("type", "GeoCoordinates");
			geo.put("lat", geoNode.findPath("latitude").asDouble(0.0));
			geo.put("lon", geoNode.findPath("longitude").asDouble(0.0));
			return Pair.of(node.findPath("id").asText(), root);
		};
	}

	private static <T> Stream<T> stream(Iterable<T> itor) {
		return StreamSupport.stream(itor.spliterator(), false);
	}

	/**
	 * @return the name of the alias of the index
	 */
	public static String getIndexAlias() {
		return indexAlias;
	}

	/**
	 * Musn't have a '-' in it.
	 * 
	 * @param indexAlias
	 */
	private static void setIndexAlias(String indexAlias) {
		if (indexAlias.contains("-")) {
			LOG.error("Index alias musn't have an '-' in it");
			return;
		}
		WikidataEntityGeodata2Es.indexAlias = indexAlias;
	}

}
