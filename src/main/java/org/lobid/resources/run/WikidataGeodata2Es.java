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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
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
public class WikidataGeodata2Es {

	private static final String JSON = "application/json";
	// TODO getter?
	public static ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
	private static final String DATE =
			new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
	private static final Logger LOG =
			LoggerFactory.getLogger(WikidataGeodata2Es.class);
	private static String indexAlias = "geo_nwbib";
	private static boolean indexExists = false;

	/**
	 * @param args ignored
	 * @throws IOException problems with the sparql query file
	 * @throws UnsupportedEncodingException problems with the sparql query file
	 */
	public static void main(String... args)
			throws UnsupportedEncodingException, IOException {
		setProductionIndexerConfigs();
		if (System.getProperty("update", "false").equals("true")) {
			esIndexer.setUpdateNewestIndex(true);
			LOG.info("Update index: true");
		} else
			esIndexer.setUpdateNewestIndex(false);
		LOG.info("Going to index");
		extractEntitiesFromSparqlQueryTranformThemAndIndex2Es(new String(
				Files.readAllBytes(Paths.get(
						"src/main/resources/getNwbibSubjectLocationsAsWikidataEntities.txt")),
				"UTF-8"));
		esIndexer.onCloseStream();
	}

	static void setProductionIndexerConfigs() {
		esIndexer.setClustername("gaia-aither");
		esIndexer.setHostname("gaia.hbz-nrw.de");
		esIndexer.setIndexName(indexAlias + "-" + DATE);
		setElasticsearchIndexer();
	}

	/**
	 * Loads a Json file into a JsonNode.
	 * 
	 * @param FILE_NAME the Json file
	 * 
	 * @return the JsonNode
	 */
	public static JsonNode jsonFile2JsonNode(final String FILE_NAME) {
		try {
			return new ObjectMapper().readTree((new File(FILE_NAME)));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
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
		setIndexAlias(indexAlias);
		esIndexer.setIndexConfig("index-config-wd-geodata.json");
		esIndexer.onSetReceiver();
	}

	private static JsonNode toApiResponse(AsyncHttpClient client, String api)
			throws InterruptedException, ExecutionException, JsonParseException,
			JsonMappingException, IOException {
		Response response = client.prepareGet(api).setHeader("Accept", JSON)
				.setFollowRedirects(true).execute().get();
		return new ObjectMapper().readValue(response.getResponseBodyAsStream(),
				JsonNode.class);
	}

	/**
	 * Starts getting the wikidata entities from a SPARQL-query and load them into
	 * elasticsearch.
	 * 
	 * @param QUERY the SPARQL-query
	 */
	public static void extractEntitiesFromSparqlQueryTranformThemAndIndex2Es(
			final String QUERY) {
		LOG.info("Lookup SPARQL-query: " + QUERY);
		try (AsyncHttpClient client = new AsyncHttpClient()) {
			JsonNode jnode = toApiResponse(client, QUERY);
			stream(jnode.get("results").get("bindings")).map(node -> {
				try {
					return toApiResponse(client,
							node.get("item").get("value").textValue());
				} catch (InterruptedException | ExecutionException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;
			}).map(transform2lobidWikidata()) //
					.forEach(index2Es());
		} catch (Exception e) {
			LOG.error("Can't get wikidata entities ", e);
		}
	}

	/**
	 * Starts getting the wikidata entities from the wikidata-API and load them
	 * into elasticsearch.
	 * 
	 * @param QUERY the SPARQL-query
	 * @return JsonNode or null
	 * 
	 */
	public static JsonNode extractEntitiesFromWikidataApiQueryAndTranformThemAndIndex2Es(
			final String QUERY) {
		try (AsyncHttpClient client = new AsyncHttpClient()) {
			JsonNode jnode = toApiResponse(client, QUERY);
			jnode = jnode.get("query").get("search").findPath("title");
			if (jnode.isMissingNode())
				LOG.info("No hit for " + QUERY);
			else {
				JsonNode jn = toApiResponse(client,
						"http://www.wikidata.org/entity/" + jnode.asText());
				Pair<String, JsonNode> lobidWikidata =
						transform2lobidWikidata().apply(jn);
				index2Es().accept(lobidWikidata);
				return lobidWikidata.second;
			}
		} catch (Exception e) {
			LOG.info("Fallback failed: " + QUERY);
		}
		return null;
	}

	/**
	 * Loads a Json-dump of wikidata entities, geodata-filtering the entities and
	 * transfoms them into a simple Json. Index the result into elasticsearch.
	 * 
	 * @param FILE_NAME the name of the Json-dump file
	 * 
	 */
	public static void filterWikidataEntitiesDump2EsGeodata(
			final String FILE_NAME) {
		LOG.info("Load wikidata json-dump: " + FILE_NAME);
		JsonNode jnode = jsonFile2JsonNode(FILE_NAME);
		stream(jnode).map(transform2lobidWikidata()) //
				.forEach(index2Es());
	}

	/**
	 * Finish loading data into elasticsearch.
	 */
	public static void finish() {
		esIndexer.updateAliases();
		esIndexer.onCloseStream();
		storeIfIndexExists(esIndexer.getElasticsearchClient());
	}

	/**
	 * Checks if index exists and stores the result in a variable, see getter @see
	 * #getIndexExists().
	 * 
	 * @param client the elasticsearch client
	 * 
	 */
	public static void storeIfIndexExists(final Client client) {
		indexExists = client.admin().indices().prepareExists(getIndexAlias())
				.execute().actionGet().isExists();
		LOG.info("Index '" + getIndexAlias() + "' existing: " + indexExists);
		if (!indexExists) {
			LOG.error("Index '" + getIndexAlias() + "' not existing: " + indexExists);
		}
	}

	private static Consumer<Pair<String, JsonNode>> index2Es() {
		return idAndJson -> {
			if (idAndJson == null)
				return;
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

	/**
	 * Filters the geo data and aliases and labels and builds a simple JSON
	 * document.
	 * 
	 * @return a Pair of String and JsonNode
	 */
	public static Function<JsonNode, Pair<String, JsonNode>> transform2lobidWikidata() {
		return node -> {
			JsonNode geoNode = node.findPath("P625").findPath("mainsnak")
					.findPath("datavalue").findPath("value");
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode root = mapper.createObjectNode();
			ObjectNode spatial = mapper.createObjectNode();
			ObjectNode geo = mapper.createObjectNode();
			root.set("spatial", spatial);
			JsonNode aliasesNode = node.findPath("aliases").findPath("de");
			if (!aliasesNode.isMissingNode())
				root.set("aliases", aliasesNode);
			spatial.put("type", "Location");
			spatial.put("id",
					"http://www.wikidata.org/entity/" + node.findPath("id").asText());
			spatial.put("label",
					node.findPath("labels").findPath("de").findPath("value").asText());
			if (!geoNode.isMissingNode()
					&& !geoNode.findPath("latitude").isMissingNode()) {
				spatial.set("geo", geo);
				geo.put("type", "GeoCoordinates");
				geo.put("lat", geoNode.findPath("latitude").asDouble(0.0));
				geo.put("lon", geoNode.findPath("longitude").asDouble(0.0));
			} else {
				LOG.info("No geo coords for "
						+ node.findPath("labels").findPath("de").findPath("value").asText()
						+ ": " + node.findPath("id").asText());
				return null;
			}
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
	 * @return the name of the alias of the index
	 */
	public static boolean getIndexExists() {
		return indexExists;
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
		WikidataGeodata2Es.indexAlias = indexAlias;
	}

}
