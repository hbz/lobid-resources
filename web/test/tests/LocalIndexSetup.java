/* Copyright 2017 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package tests;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.google.common.io.CharStreams;

import controllers.resources.Index;
import play.Logger;
import play.libs.Json;

/**
 * Setup for the search tests. Creates a local ES index with test data.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
public abstract class LocalIndexSetup {

	private static final String TEST_CONFIG = //
			"../src/main/resources/index-config.json";
	private static final String TEST_DATA = //
			"../src/test/resources/jsonld";
	private static Node node;
	protected static Client client;
	private static final String TEST_INDEX = "resources";

	@BeforeClass
	public static void setup() {
		node = nodeBuilder().local(true)
				.settings(Settings.settingsBuilder()//
						.put("index.number_of_replicas", "0")//
						.put("index.number_of_shards", "1")//
						.put("path.home", "test/resources/index").build())
				.node();
		client = node.client();
		client.admin().indices().prepareDelete("_all").execute().actionGet();
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute()
				.actionGet();
		File sampleDataRoot = new File(TEST_DATA);
		BulkRequestBuilder bulkRequest = client.prepareBulk();
		readTestData(sampleDataRoot, bulkRequest);
		final List<BulkItemResponse> failed = new ArrayList<>();
		if (bulkRequest.numberOfActions() > 0)
			runBulkRequest(bulkRequest, failed);
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
		Index.elasticsearchClient = client;
	}

	@AfterClass
	public static void down() {
		client.admin().indices().prepareDelete(TEST_INDEX).execute().actionGet();
		node.close();
		Index.elasticsearchClient = null;
	}

	private static void readTestData(File sampleDataRoot,
			BulkRequestBuilder bulkRequest) {
		Arrays.asList(sampleDataRoot.listFiles()).forEach((file) -> {
			try {
				String data = Files.readAllLines(Paths.get(file.getAbsolutePath()))
						.stream().map(String::trim).collect(Collectors.joining());
				boolean isItem = file.getName().contains(":");
				String type = isItem ? "item" : "resource";
				String id = file.getName();
				String parent = isItem
						? "http://lobid.org/resources/" + file.getName().split(":")[0] : "";
				final Map<String, Object> map =
						Json.fromJson(Json.parse(data), Map.class);
				final IndexRequestBuilder requestBuilder =
						createRequestBuilder(type, id, parent, map);
				bulkRequest.add(requestBuilder);
			} catch (Exception e) {
				Logger.error("Error reading file {}: {}", file, e.getMessage());
			}
		});
	}

	private static void runBulkRequest(final BulkRequestBuilder bulkRequest,
			final List<BulkItemResponse> result) {
		final BulkResponse bulkResponse = bulkRequest.execute().actionGet();
		if (bulkResponse == null) {
			Logger.error("Bulk request failed: " + bulkRequest);
		} else {
			collectFailedResponses(result, bulkResponse);
		}
	}

	private static void collectFailedResponses(
			final List<BulkItemResponse> result, final BulkResponse bulkResponse) {
		for (BulkItemResponse response : bulkResponse) {
			if (response.isFailed()) {
				Logger.error(String.format(
						"Bulk item response failed for index '%s', ID '%s', message: %s",
						response.getIndex(), response.getId(),
						response.getFailureMessage()));
				result.add(response);
			}
		}
	}

	private static IndexRequestBuilder createRequestBuilder(final String type,
			String id, String parent, final Map<String, Object> map) {
		final IndicesAdminClient admin = client.admin().indices();
		if (!admin.prepareExists(TEST_INDEX).execute().actionGet().isExists()) {
			admin.prepareCreate(TEST_INDEX).setSource(config()).execute().actionGet();
		}
		final IndexRequestBuilder request =
				client.prepareIndex(TEST_INDEX, type, id).setSource(map);
		return parent.isEmpty() ? request : request.setParent(parent);
	}

	private static String config() {
		String res = null;
		try {
			final InputStream config = new FileInputStream(TEST_CONFIG);
			try (InputStreamReader reader = new InputStreamReader(config, "UTF-8")) {
				res = CharStreams.toString(reader);
			}
		} catch (IOException e) {
			Logger.error(e.getMessage(), e);
		}
		return res;
	}
}
