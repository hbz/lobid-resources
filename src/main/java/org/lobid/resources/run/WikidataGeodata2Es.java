/* Copyright 2015-2018 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Client;
import org.lobid.resources.ElasticsearchIndexer;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

	private static final String HTTP_WWW_WIKIDATA_ORG_ENTITY =
			"http://www.wikidata.org/entity/";
	private static final String JSON_ACCEPT_HEADER = "application/json";
	/** managing indexing */
	public static ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
	private static final String DATE =
			new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
	private static final Logger LOG =
			LogManager.getLogger(WikidataGeodata2Es.class);
	private static HashMap<String, String> qidMap = new HashMap<>();
	private static BufferedReader lineReader;
	/** This is the root node of the geo data. */
	public static final String FOCUS = "focus";
	private final static String INDEX_ALIAS_PREFIX = "geo_nwbib";
	private static String indexAliasSuffix = "";
	private static boolean indexExists = false;
	private final static String NWBIB_SPATIAL_PREFIX =
			"https://nwbib.de/spatial#";
	private static String qidCsvFn = "src/main/resources/string2wikidata.tsv";

	/**
	 * This maps the nwbib location codes to wikidata entities.
	 */
	public static HashMap<String, String> NWBIB_LOCATION_CODES_2_WIKIDATA_ENTITIES =
			new HashMap<String, String>() {
				private static final long serialVersionUID = 12L;

				{
					put("99",
							"Q22865 Q262166 Q253019 Q1852178 Q15632166 Q2983893 Q42744322 Q134626 Q448801 Q1548518 Q54935786");
					put("97", "Q106658 Q5283531 Q1780389");
					put("96", "Q829277");
					put("36", "Q3146899 Q2072238");
					put("35", "Q897303");

				}
			};
	/**
	 * This provides a boost map for literal queries to be more precisely scored.
	 */
	public static HashMap<String, Float> FIELD_BOOST =
			new HashMap<String, Float>() {
				private static final long serialVersionUID = 13L;

				{
					put("label", 10.0f);
					put("locatedIn.value", 1.0f);
					put("aliases.value", 2.0f);
					put(FOCUS + ".type", 4.0f);
				}
			};
	/**
	 * This provides a boost map for type queries to be more precisely scored.
	 */
	public static HashMap<String, Float> TYPE_QUERY =
			new HashMap<String, Float>() {
				private static final long serialVersionUID = 14L;

				{
					put(FOCUS + ".type", 4.0f);
				}
			};

	/**
	 * @param args ignored
	 * @throws IOException problems with the sparql query file
	 * @throws UnsupportedEncodingException problems with the sparql query file
	 */
	public static void main(String... args)
			throws UnsupportedEncodingException, IOException {
		String indexName = INDEX_ALIAS_PREFIX + "-" + DATE;
		if (!System.getProperty("indexName", "").isEmpty()) {
			indexName = System.getProperty("indexName");
		}
		if (System.getProperty("update", "false").equals("true")) {
			esIndexer.setUpdateNewestIndex(true);
			LOG.info("Going to update index, not creating a new one");
		} else {
			esIndexer.setUpdateNewestIndex(false);
			LOG.info("Going to create a new index, not updating an existing one");
		}
		String aliasSuffix = System.getProperty("aliasSuffix", "");
		LOG.info("Alias suffix configured:'" + aliasSuffix + "' ...");
		LOG.info("... so the alias is: '" + INDEX_ALIAS_PREFIX + aliasSuffix + "'");
		esIndexer.setIndexAliasSuffix(aliasSuffix);
		setProductionIndexerConfigs(indexName);
		LOG.info("Going to index");
		loadQidMap();
		qidMap.values().stream()
				.forEach(value -> getQidTranformThemAndIndex2Es(value));
		extractEntitiesFromSparqlQueryTranformThemAndIndex2Es((new String(
				Files.readAllBytes(Paths.get(
						"src/main/resources/getNwbibSubjectLocationsAsWikidataEntities.sparql")),
				"UTF-8")).replaceAll("#.*\\n", ""));

		esIndexer.onCloseStream();
	}

	/**
	 * Loads the manually created QID map.
	 * 
	 */
	public static void loadQidMap() {
		LOG.info("going to load QID csv from " + qidCsvFn + "...");
		String line = null;
		try {
			lineReader = new BufferedReader(new FileReader(qidCsvFn));
			line = lineReader.readLine();
			while (line != null) {
				try {
					String[] stringQidCsv = line.split("\t");
					qidMap.put(
							stringQidCsv[0].replaceAll("[^\\p{IsAlphabetic}]", " ").trim(),
							stringQidCsv[1]);
				} catch (Exception e) {
					LOG.warn("Missing QID in " + line);
				}
				line = lineReader.readLine();
			}
		} catch (Exception e) {
			LOG.warn(e.getMessage() + "\n" + line);
		}
		LOG.info("... loaded " + qidMap.size() + " entries from QID csv.");
	}

	static void setProductionIndexerConfigs(final String INDEX_NAME) {
		esIndexer.setClustername("weywot");
		esIndexer.setHostname("weywot5.hbz-nrw.de");
		LOG.info("Set index-name to: " + INDEX_NAME);
		esIndexer.setIndexName(INDEX_NAME);
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
	 * Initialize the class with a given client. Only used by junit tests.
	 * 
	 * @param client the elasticsearch client
	 */
	public static void setElasticsearchIndexer(Client client) {
		esIndexer.setElasticsearchClient(client);
		esIndexer.setIndexAliasSuffix("NOALIAS");
		setElasticsearchIndexer();
	}

	private static void setElasticsearchIndexer() {
		esIndexer.setIndexConfig("index-config-wd-geodata.json");
		esIndexer.onSetReceiver();
	}

	private static JsonNode toApiResponseGet(final AsyncHttpClient CLIENT,
			final String API) throws InterruptedException, ExecutionException,
			JsonParseException, JsonMappingException, IOException {
		Thread.sleep(200); // be nice throttle down
		LOG.info("Lookup QID: " + API);
		Response response =
				CLIENT.prepareGet(API).setHeader("Accept", JSON_ACCEPT_HEADER)
						.setFollowRedirects(true).execute().get();
		return new ObjectMapper().readValue(response.getResponseBodyAsStream(),
				JsonNode.class);
	}

	private static JsonNode toApiResponsePost(final AsyncHttpClient CLIENT,
			final String URL, final String QUERY)
			throws InterruptedException, ExecutionException, JsonParseException,
			JsonMappingException, IOException {
		LOG.info("SPARQL-URL=" + URL);
		LOG.info("SPARQL-query=" + QUERY);
		Response response = CLIENT.preparePost(URL).addFormParam("query", QUERY)
				.setHeader("Accept", JSON_ACCEPT_HEADER).setFollowRedirects(true)
				.execute().get();
		return new ObjectMapper().readValue(response.getResponseBodyAsStream(),
				JsonNode.class);
	}

	/**
	 * Lookups a wikidata entity and, transform this to geo-nwbib-cache structure
	 * and load into elasticsearch.
	 * 
	 * @param QID the wikidata Q-ID
	 */
	public static void getQidTranformThemAndIndex2Es(final String QID) {
		try (AsyncHttpClient client = new AsyncHttpClient()) {
			JsonNode jnode =
					toApiResponseGet(client, "http://www.wikidata.org/entity/" + QID);
			stream(jnode).map(transform2lobidWikidata()) //
					.forEach(index2Es());
		} catch (Exception e) {
			LOG.error("Can't get wikidata entities ", e);
		}
	}

	/**
	 * Starts getting the wikidata entities from a SPARQL-query and load them into
	 * elasticsearch.
	 * 
	 * @param QUERY the SPARQL-query
	 */
	public static void extractEntitiesFromSparqlQueryTranformThemAndIndex2Es(
			final String QUERY) {
		LOG.info("Lookup SPARQL-query ...");
		try (AsyncHttpClient client = new AsyncHttpClient()) {
			JsonNode jnode =
					toApiResponsePost(client, QUERY.substring(0, QUERY.indexOf("\n")),
							QUERY.replaceFirst(".*\n", ""));
			stream(jnode.get("results").get("bindings")).map(node -> {
				try {
					return toApiResponseGet(client,
							node.get("item").get("value").textValue());
				} catch (InterruptedException | ExecutionException | IOException e) {
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
			try {
				if (idAndJson == null) {
					return;
				}
				HashMap<String, String> jsonMap = new HashMap<>();
				jsonMap.put(ElasticsearchIndexer.Properties.TYPE.getName(),
						"wikidata-geo");
				jsonMap.put(ElasticsearchIndexer.Properties.GRAPH.getName(),
						idAndJson.second.toString());
				jsonMap.put(ElasticsearchIndexer.Properties.ID.getName(),
						idAndJson.first);
				esIndexer.process(jsonMap);
			} catch (Exception e) {
				LOG.error("Couldn't index a json document from the wd-entity ", e);
			}
		};
	}

	private static HashMap<String, JsonNode> locatedInMapCache = new HashMap<>();
	private static ArrayNode conceptNode =
			new ObjectMapper().createArrayNode().add("Concept");
	private static ObjectNode sourceNode = new ObjectMapper().createObjectNode()
			.put("id", "https://nwbib.de/spatial")
			.put("label", "Raumsystematik der Nordrhein-Westfälischen Bibliographie");

	/**
	 * Filters the geo data and aliases and labels, gets the superordinated
	 * locatedIn-node and builds a simple JSON document.
	 * 
	 * @return a Pair of Id and JsonNode
	 */
	public static Function<JsonNode, Pair<String, JsonNode>> transform2lobidWikidata() {
		return node -> {
			ObjectNode root = null;
			String id;
			try {
				JsonNode geoNode = node.findPath("P625").findPath("mainsnak")
						.findPath("datavalue").findPath("value");
				ObjectMapper mapper = new ObjectMapper();
				root = mapper.createObjectNode();
				ObjectNode focus = mapper.createObjectNode();
				ObjectNode geo = mapper.createObjectNode();
				root.set(FOCUS, focus);
				JsonNode aliasesNode = node.findPath("aliases").findPath("de");
				if (!aliasesNode.isMissingNode())
					root.set("aliases", aliasesNode);
				ArrayNode type = mapper.createObjectNode().arrayNode();
				try {
					id = node.with("entities").fieldNames().next();
				} catch (NoSuchElementException ex) {
					id = node.findPath("id").asText();
				}
				focus.put("id", HTTP_WWW_WIKIDATA_ORG_ENTITY + id);
				root.put("id", NWBIB_SPATIAL_PREFIX + id);
				root.set("type", conceptNode);
				root.put("label",
						node.findPath("labels").findPath("de").findPath("value").asText());
				root.set("source", sourceNode);
				if (!geoNode.isMissingNode()
						&& !geoNode.findPath("latitude").isMissingNode()) {
					focus.set("geo", geo);
					geo.put("lat", geoNode.findPath("latitude").asDouble(0.0));
					geo.put("lon", geoNode.findPath("longitude").asDouble(0.0));
				} else {
					LOG.info("No geo coords for " + node.findPath("labels").findPath("de")
							.findPath("value").asText() + ": "
							+ node.findPath("id").asText());
				}
				String locatedInId = node.findPath("P131").findPath("mainsnak")
						.findPath("datavalue").findPath("value").findPath("id").asText();
				if (!locatedInId.isEmpty()) {
					JsonNode locatedInNode;
					if (locatedInMapCache.containsKey(locatedInId)) {
						locatedInNode = locatedInMapCache.get(locatedInId);
					} else {
						try (AsyncHttpClient client = new AsyncHttpClient()) {
							JsonNode jnode = toApiResponseGet(client,
									HTTP_WWW_WIKIDATA_ORG_ENTITY + locatedInId);
							String locatedInLabel = jnode.findPath("labels").findPath("de")
									.findPath("value").asText();
							LOG.debug("Found locatedIn id:" + locatedInId + " with label "
									+ locatedInLabel);
							locatedInNode = jnode.findPath("labels").findPath("de");
							locatedInMapCache.put(locatedInId, locatedInNode);
						}
					}
					root.set("locatedIn", locatedInNode);
				}
				List<JsonNode> typeNode = node.findValues("P31");
				if (!typeNode.isEmpty()) {
					typeNode.parallelStream()
							.forEach(e -> e.findValues("mainsnak")
									.forEach(e1 -> type.add(HTTP_WWW_WIKIDATA_ORG_ENTITY
											+ e1.findPath("datavalue").findPath("id").asText())));
					focus.set("type", type);
				}
				LOG.debug("Wikidata-Type extracted for type " + type.toString());
			} catch (Exception e) {
				LOG.error("Couldn't build a json document from the wd-entity ", e);
				return null;
			}
			return Pair.of(id, root);
		};
	}

	private static <T> Stream<T> stream(Iterable<T> itor) {
		return StreamSupport.stream(itor.spliterator(), false);
	}

	/**
	 * @return the name of the alias of the index
	 */
	public static String getIndexAlias() {
		return INDEX_ALIAS_PREFIX + indexAliasSuffix;
	}

	/**
	 * @return the suffix of the name of the alias of the index
	 */
	public static String getIndexAliasSuffix() {
		return indexAliasSuffix;
	}

	/**
	 * @return the name of the alias of the index
	 */
	public static boolean getIndexExists() {
		return indexExists;
	}

	/**
	 * Sets an optional suffix to the elasticsearch index alias.
	 *
	 * @param INDEX_ALIAS_SUFFIX the suffix of the alias of this index
	 */
	public static void setIndexAliasSuffix(final String INDEX_ALIAS_SUFFIX) {
		indexAliasSuffix = INDEX_ALIAS_SUFFIX;
	}

	/**
	 * @return HashMap of the string/Qid mapping
	 */
	public static HashMap<String, String> getQidMap() {
		return qidMap;
	}

}
