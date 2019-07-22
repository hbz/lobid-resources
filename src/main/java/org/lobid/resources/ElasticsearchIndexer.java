/* Copyright 2013-2015 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources;

import static org.elasticsearch.common.xcontent.XContentType.JSON;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequestBuilder;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.rest.action.admin.indices.AliasesNotFoundException;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.lobid.resources.run.WikidataGeodata2Es;
import org.metafacture.framework.ObjectReceiver;
import org.metafacture.framework.annotations.In;
import org.metafacture.framework.annotations.Out;
import org.metafacture.framework.helpers.DefaultObjectPipe;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.gdata.util.common.base.Pair;
import com.google.gdata.util.common.io.CharStreams;

/**
 * Index JSON into elasticsearch.
 * 
 * @author Pascal Christoph (dr0i)
 * @author Fabian Steeg (fsteeg)
 */
@In(HashMap.class)
@Out(Void.class)
public class ElasticsearchIndexer
		extends DefaultObjectPipe<HashMap<String, String>, ObjectReceiver<Void>> {

	private static final Logger LOG =
			LogManager.getLogger(ElasticsearchIndexer.class);
	private String hostname;
	private String clustername;
	private BulkRequestBuilder bulkRequest;
	private InetSocketTransportAddress NODE;
	private IndexRequest indexRequest;
	private TransportClient tc;
	private Client client;
	private int retries = 40;
	// collect so many documents before bulk indexing them all
	private int bulkSize = 5000;
	private int docs = 0;
	private String indexName;
	private boolean updateNewestIndex;
	private String aliasSuffix = "";
	private static String indexConfig;
	private static ObjectMapper mapper = new ObjectMapper();
	/** Defines the threshold for wikidata lookups */
	public static double MINIMUM_SCORE = 1.0;
	private HashMap<String, Object> settings = new HashMap<>();
	/** Defines if the mabxml lookup should be done */
	public boolean lookupMabxmlDeletion;
	/** Defines if a wikidata lookup should be done */
	public boolean lookupWikidata;
	private static HashSet<String> unsuccessfullyLookup = new HashSet<>();
	private static final LocalDateTime now = LocalDateTime.now();
	private static final int REPLICA = 1;
	private static final String REFRESH_INTERVALL = "30s";

	/**
	 * The date now. Handy to append to index-name to build multiple index' in
	 * parallel. Switch then by setting the alias.
	 */
	public static final String DATE =
			now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
					+ now.getHour() + now.getMinute() + now.getSecond();

	/**
	 * Keys to get index properties and the json document ("graph")
	 */
	@SuppressWarnings("javadoc")
	public static enum Properties {
		INDEX("_index"), TYPE("_type"), ID("_id"), PARENT("_parent"), GRAPH(
				"graph");
		private final String name;

		Properties(final String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	@Override
	public void onCloseStream() {
		LOG.info("Finishing indexing of ES index '" + indexName + "' ...");
		// remove old and unprotected indices
		if (!aliasSuffix.equals("NOALIAS") && !updateNewestIndex
				&& !aliasSuffix.toLowerCase().contains("test"))
			updateAliases();
		// feed the rest of the bulk
		if (bulkRequest.numberOfActions() != 0) {
			BulkResponse bulkResponse = bulkRequest.execute().actionGet();
			if (bulkResponse.hasFailures()) {
				LOG.warn("Bulk insert failed: " + bulkResponse.buildFailureMessage());
			}
		}
		probablyUpdateSettings();
		LOG.info("... finished indexing of ES index '" + indexName + "'");
	}

	private void probablyUpdateSettings() {
		UpdateSettingsRequestBuilder usrb =
				client.admin().indices().prepareUpdateSettings();
		usrb.setIndices(indexName);
		GetSettingsResponse response =
				client.admin().indices().prepareGetSettings(indexName).get();
		for (ObjectObjectCursor<String, Settings> cursor : response
				.getIndexToSettings()) {
			Settings settingsOfIndex = cursor.value;
			Integer shards = settingsOfIndex.getAsInt("index.number_of_shards", null);
			Integer replicas =
					settingsOfIndex.getAsInt("index.number_of_replicas", null);
			String refresh = settingsOfIndex.get("index.refresh_interval", null);
			LOG.info("Get index '" + indexName + "', it has shards:" + shards
					+ ", replicas:" + replicas + ", refresh intervall:" + refresh);
			// set replicas to 1 and refresh intervall to 30s
			if (!refresh.equals(REFRESH_INTERVALL) || !replicas.equals(REPLICA)) {
				LOG.info("Set '" + indexName + "' to replicas:" + REPLICA
						+ ", refresh intervall:" + REFRESH_INTERVALL);
				settings.put("index.refresh_interval", REFRESH_INTERVALL);
				settings.put("index.number_of_replicas", REPLICA);
				usrb.setSettings(settings);
				usrb.execute().actionGet();
			}
		}
	}

	@Override
	public void onSetReceiver() {
		if (client == null) {
			LOG.info("clustername=" + this.clustername);
			LOG.info("hostname=" + this.hostname);
			Settings nodeSettings = Settings.builder()
					.put("cluster.name", this.clustername)
					.put("client.transport.sniff", false)
					.put("client.transport.ping_timeout", 120, TimeUnit.SECONDS).build();
			try {
				this.NODE = new InetSocketTransportAddress(
						InetAddress.getByName(this.hostname), 9300);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			this.tc = new PreBuiltTransportClient(nodeSettings);
			this.client = this.tc.addTransportAddress(this.NODE);
		}
		bulkRequest = client.prepareBulk();
		if (!indexExists()) {
			LOG.info("Creating new index");
			LOG.info("Set index.number_of_replicas to 0");
			settings.put("index.number_of_replicas", 0);
		}
		if (updateNewestIndex) {
			if (indexName == null)
				getNewestIndex();
		} else
			createIndex();
		UpdateSettingsRequest request = new UpdateSettingsRequest(indexName);

		LOG.info("Set index.refresh_interval to -1");
		settings.put("index.refresh_interval", "-1");
		request.settings(settings);
		client.admin().indices().updateSettings(request).actionGet();
		if (lookupWikidata) {
			WikidataGeodata2Es.setIndexAliasSuffix(
					aliasSuffix.equals("NOALIAS") ? "" : aliasSuffix);
			LOG.info("Using wikidata geo_nwbib index with name:"
					+ WikidataGeodata2Es.getIndexAlias());
			LOG.info("Start loading manually created Qid map ...");
			WikidataGeodata2Es.loadQidMap();
			LOG.info("Finished loading created Qid map loaded.");
			LOG.info(
					"Threshold minimum score for spatial enrichment: " + MINIMUM_SCORE);
		}
	}

	private boolean indexExists() {
		return client.admin().indices().prepareExists(indexName).execute()
				.actionGet().isExists();
	}

	@Override
	public void process(final HashMap<String, String> json) {
		LOG.debug("Try to index " + json.get(Properties.ID.getName())
				+ " in ES type " + json.get(Properties.TYPE.getName()) + " Source:"
				+ json.get(Properties.GRAPH.getName()));
		indexRequest = new IndexRequest(indexName,
				json.get(Properties.TYPE.getName()), json.get(Properties.ID.getName()));
		String jsonDoc = json.get(Properties.GRAPH.getName());
		if (json.containsKey(Properties.PARENT.getName())) { // items
			indexRequest.parent(json.get(Properties.PARENT.getName()));
		} else {
			if (lookupWikidata) {
				try {
					ObjectNode node = mapper.readValue(
							json.get(Properties.GRAPH.getName()), ObjectNode.class);
					jsonDoc = enrich(WikidataGeodata2Es.getIndexAlias(), "coverage",
							"spatial", node);
				} catch (IOException e1) {
					LOG.info(
							"Enrichment problem with" + json.get(Properties.ID.getName()),
							e1);
				}
			} else if (lookupMabxmlDeletion) {
				jsonDoc = enrichMabxmlDeletions(json.get(Properties.ID.getName())
						.replaceAll(".*/", "").replaceAll("#!", ""), jsonDoc);
			}
		}
		indexRequest.source(jsonDoc, JSON);
		bulkRequest.add(indexRequest);
		docs++;

		while (docs > bulkSize && retries > 0) {
			try {
				BulkResponse bulkResponse = bulkRequest.execute().actionGet();
				if (bulkResponse.hasFailures()) {
					LOG.warn("Bulk insert failed: " + bulkResponse.buildFailureMessage());
				}
				docs = 0;
				bulkRequest = client.prepareBulk();
				break; // stop retry-while
			} catch (final NoNodeAvailableException e) {
				retries--;
				try {
					Thread.sleep(10000);
				} catch (final InterruptedException x) {
					x.printStackTrace();
				}
				LOG.warn("Retry indexing record" + json.get(Properties.ID.getName())
						+ ":" + e.getMessage() + " (" + retries + " more retries)");
			} catch (final Exception ex) {
				LOG.warn(ex);
			}
		}
	}

	/*
	 * Replace all aleph internal sysnumbers with lobid resources ids.
	 */
	private String enrichMabxmlDeletions(String alephId, String node) {
		String ret = null;
		try {
			JsonNode jnode = mapper.readTree(node);
			QueryStringQueryBuilder query =
					QueryBuilders.queryStringQuery("alephInternalSysnumber:"
							+ jnode.findValue("alephInternalSysnumber"));
			SearchHits hits = null;
			hits = getElasticsearchClient().prepareSearch("hbz01").setQuery(query)
					.get().getHits();
			if (hits.getTotalHits() > 0) {
				ret = node.toString().replaceAll("/" + alephId,
						"/" + hits.getAt(0).getId());
			}
		} catch (Exception e) {
			LOG.warn(e.getMessage(), node);
		}
		System.out.println(ret);
		return ret;
	}

	private String enrich(final String index, final String queryField,
			final String SPATIAL, ObjectNode node) {
		Iterable<Entry<String, JsonNode>> iterable = () -> node.fields();
		Optional<Entry<String, JsonNode>> o =
				StreamSupport.stream(iterable.spliterator(), false)
						.filter(k -> k.getKey().equals(queryField)).findFirst();
		if (o.isPresent()) {
			String[] coverage = o.get().getValue().toString().split("\",\"");
			ArrayNode spatialNode = node.withArray(SPATIAL);
			HashSet<String> wdIds = new HashSet<>();
			for (int i = 0; i < coverage.length; i++) {
				Pair<String, String> query = new Pair<>(
						coverage[i].replaceAll("[^\\p{IsAlphabetic}]", " ").trim(),
						WikidataGeodata2Es.NWBIB_LOCATION_CODES_2_WIKIDATA_ENTITIES
								.getOrDefault(
										coverage[i].replaceAll("[^\\p{Digit}]", " ").trim(), ""));
				try {
					if (unsuccessfullyLookup.contains(query.first + query.second))
						throw new Exception(
								"Already lookuped with no good result, skipping");
					SearchHits hits = null;
					QueryBuilder queryBuilded;
					String string2wikidataTsv;
					if ((string2wikidataTsv =
							WikidataGeodata2Es.getQidMap().get(query.first)) != null)
						queryBuilded = QueryBuilders.idsQuery().addIds(string2wikidataTsv);
					else
						queryBuilded = QueryBuilders.boolQuery()
								.must(new MultiMatchQueryBuilder(query.first)
										.fields(WikidataGeodata2Es.FIELD_BOOST)
										.type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
										.operator(Operator.AND))
								.must(new MultiMatchQueryBuilder(query.second)
										.fields(WikidataGeodata2Es.TYPE_QUERY));
					hits = client.prepareSearch(index).setQuery(queryBuilded).get()
							.getHits();

					if (hits.getTotalHits() > 0) {
						ObjectNode newSpatialNode = mapper
								.readValue(hits.getAt(0).getSourceAsString(), ObjectNode.class);
						newSpatialNode.remove("locatedIn");
						newSpatialNode.remove("aliases");
						LOG.info(i + " 1.Hit Query=" + query + " score="
								+ hits.getAt(0).getScore() + " source="
								+ newSpatialNode.toString());
						if (hits.getAt(0).getScore() < MINIMUM_SCORE) {
							unsuccessfullyLookup.add(query.first + query.second);
							throw new Exception(
									"Score " + hits.getAt(0).getScore() + " to low.");
						}
						String wdId = newSpatialNode.findPath("id").toString();
						if (!wdIds.contains(wdId)) { // add entity only once
							spatialNode.add(newSpatialNode);
							wdIds.add(wdId);
						}
					} else
						throw new Exception("No hit.");
				} catch (Exception e) {
					LOG.warn("Couldn't get a hit using index '" + index + "' querying '"
							+ query + "'. " + e.getMessage());
					unsuccessfullyLookup.add(query.first + query.second);
				}
			}
			if (spatialNode.size() > 0)
				node.set(SPATIAL, spatialNode);
			else
				node.remove(SPATIAL);
		}
		return node.toString();
	}

	/**
	 * Sets the name of the index config json filename.
	 * 
	 * @param indexConfig the filename of the index config
	 */
	public void setIndexConfig(final String indexConfig) {
		ElasticsearchIndexer.indexConfig = indexConfig;
	}

	/**
	 * Sets the elasticsearch cluster name.
	 * 
	 * @param clustername the name of the cluster
	 */
	public void setClustername(final String clustername) {
		this.clustername = clustername;
	}

	/**
	 * Sets the elasticsearch hostname
	 * 
	 * @param hostname may be an IP or a domain name
	 */
	public void setHostname(final String hostname) {
		this.hostname = hostname;
	}

	/**
	 * Sets the elasticsearch index name
	 * 
	 * @param indexname name of the index
	 */
	public void setIndexName(final String indexname) {
		this.indexName = indexname;
	}

	/**
	 * Sets an optional suffix to the elasticsearch index alias.
	 * 
	 * @param aliasSuffix
	 */
	public void setIndexAliasSuffix(String aliasSuffix) {
		this.aliasSuffix = aliasSuffix;
	}

	/**
	 * Sets the elasticsearch client.
	 * 
	 * @param client the elasticsearch client
	 */
	public void setElasticsearchClient(Client client) {
		this.client = client;
	}

	/**
	 * Sets the elasticsearch client.
	 * 
	 * @return client the elasticsearch client
	 * 
	 */
	public Client getElasticsearchClient() {
		return this.client;
	}

	/**
	 * Sets a flag wether the index alias(es) should be updated
	 * 
	 * @param updateIndex name of the index
	 */
	public void setUpdateNewestIndex(final boolean updateIndex) {
		this.updateNewestIndex = updateIndex;
	}

	private void getNewestIndex() {
		String indexNameWithoutTimestamp = indexName.replaceAll("20.*", "");
		final SortedSetMultimap<String, String> indices =
				groupByIndexCollection(indexName);
		for (String prefix : indices.keySet()) {
			final SortedSet<String> indicesForPrefix = indices.get(prefix);
			final String newestIndex = indicesForPrefix.last();
			if (newestIndex.startsWith(indexNameWithoutTimestamp))
				indexName = newestIndex;
		}
		LOG.info("Going to UPDATE existing index " + indexName);
	}

	private void createIndex() {
		if (!indexExists()) {
			LOG.info("Going to CREATE new index " + indexName + " at cluster "
					+ this.client.settings().get("cluster.name"));
			client.admin().indices().prepareCreate(indexName)
					.setSource(config(), JSON).execute().actionGet();
			settings.put("index.number_of_replicas", 0);
		} else
			LOG.info("Index already exists, going to UPDATE index " + indexName);
	}

	private static String config() {
		String res = null;
		try {
			final InputStream config =
					Thread.currentThread().getContextClassLoader().getResourceAsStream(
							indexConfig == null ? "index-config.json" : indexConfig);
			try (InputStreamReader reader = new InputStreamReader(config, "UTF-8")) {
				res = CharStreams.toString(reader);
			}
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
		return res;
	}

	/**
	 * Updates alias, may remove old indices.
	 * 
	 */
	public void updateAliases() {
		final SortedSetMultimap<String, String> indices =
				groupByIndexCollection(indexName);
		for (String prefix : indices.keySet()) {
			final SortedSet<String> indicesForPrefix = indices.get(prefix);
			final String newestIndex = indicesForPrefix.last();
			final String newAlias = prefix + aliasSuffix;
			LOG.info("Prefix " + prefix + ", newest index: " + newestIndex);
			if (!newestIndex.equals(newAlias)) {
				removeAlias(indicesForPrefix, newAlias);
				createNewAlias(newestIndex, newAlias);
			}
			deleteOldIndices(indexName, indicesForPrefix);
		}
	}

	/**
	 * This adds the productive prefix as alias to the newest index (which names
	 * begins with productive prefix) and adds the staging alias to the second
	 * newest index name.
	 * 
	 * @param productivePrefix the productive alias (and also the prefix of the
	 *          names of the indices)
	 * @param staging the "-staging" alias
	 */
	public void swapProductionAndStagingAliases(final String productivePrefix,
			final String staging) {
		final SortedSet<String> indicesWithProductionPrefix =
				groupByIndexCollection(productivePrefix).get(productivePrefix);
		final String newestIndex = indicesWithProductionPrefix.last();
		String secondNewestIndex = (String) indicesWithProductionPrefix
				.toArray()[indicesWithProductionPrefix.size() - 2];
		createNewAlias(newestIndex, productivePrefix);
		createNewAlias(secondNewestIndex, staging);
		removeAlias(newestIndex, staging);
		removeAlias(secondNewestIndex, productivePrefix);
	}

	private SortedSetMultimap<String, String> groupByIndexCollection(
			final String name) {
		final SortedSetMultimap<String, String> indices = TreeMultimap.create();
		for (String index : client.admin().indices().prepareStats().execute()
				.actionGet().getIndices().keySet()) {
			final String[] nameAndTimestamp = index.split("-(?=\\d)");
			if (name.startsWith(nameAndTimestamp[0]))
				indices.put(nameAndTimestamp[0], index);
		}
		return indices;
	}

	private void removeAlias(final String index, final String alias) {
		try {
			LOG.info("Deleting alias '" + alias + "' from index '" + index + "' ...");
			client.admin().indices().prepareAliases().removeAlias(index, alias)
					.execute().actionGet();
			LOG.info("... deleted alias '" + alias + "' from index '" + index + "'");
		} catch (AliasesNotFoundException e) {
			LOG.info("... there was no alias '" + alias + "' set on index '" + index
					+ "'");
		}
	}

	private void removeAlias(final SortedSet<String> indicesForPrefix,
			final String newAlias) {
		try {
			for (String name : indicesForPrefix) {
				final Set<String> aliases = aliases(name);
				for (String alias : aliases) {
					if (alias.equals(newAlias)) {
						removeAlias(name, alias);
					}
				}
			}
		} catch (AliasesNotFoundException e) {
			LOG.warn("Alias not found", e);
		}
	}

	private void createNewAlias(final String newIndex, final String newAlias) {
		LOG.info("Create alias " + newAlias + " for index " + newIndex);
		client.admin().indices().prepareAliases().addAlias(newIndex, newAlias)
				.execute().actionGet();
	}

	private void deleteOldIndices(final String name,
			final SortedSet<String> allIndices) {
		if (allIndices.size() >= 3) {
			final List<String> list = new ArrayList<>(allIndices);
			list.remove(name);
			for (String indexToDelete : list) {
				boolean hasAlias = client.admin().cluster()
						.state(Requests.clusterStateRequest().nodes(true)
								.indices(indexToDelete))
						.actionGet().getState().getMetaData()
						.hasAliases(new String[] { "*" }, new String[] { indexToDelete });
				if (!hasAlias) {
					client.admin().indices().delete(new DeleteIndexRequest(indexToDelete))
							.actionGet();
					LOG.info("Deleting index: " + indexToDelete);
				}
			}
		}
	}

	private Set<String> aliases(final String name) {
		final ClusterStateRequest clusterStateRequest =
				Requests.clusterStateRequest().nodes(true).indices(name);
		return client.admin().cluster().state(clusterStateRequest).actionGet()
				.getState().getMetaData().getAliasAndIndexLookup().keySet();
	}

}
