package controllers.resources;

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
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;

import com.google.common.io.CharStreams;

import play.Logger;
import play.libs.Json;

/**
 * Set up a local Elasticsearch index for testing
 * 
 * @author fsteeg
 *
 */
public class LocalIndex {

	private static final String TEST_CONFIG =
			"../src/main/resources/index-config.json";
	private static final String TEST_DATA = "../src/test/resources/jsonld";

	private Node node;
	private Client client;

	private static final String TEST_INDEX = "resources";

	/**
	 * Create a new local index based on our test set
	 */
	public LocalIndex() {
		node = new Node(Settings.builder()
				.put(Node.NODE_NAME_SETTING.getKey(), "testNode")
				.put(NetworkModule.TRANSPORT_TYPE_KEY, NetworkModule.LOCAL_TRANSPORT)
				.put(NetworkModule.HTTP_ENABLED.getKey(), false) //
				.put(Environment.PATH_HOME_SETTING.getKey(), "test/resources/index")//
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
		File sampleDataRoot = new File(TEST_DATA);
		BulkRequestBuilder bulkRequest = client.prepareBulk();
		readTestData(sampleDataRoot, bulkRequest);
		final List<BulkItemResponse> failed = new ArrayList<>();
		if (bulkRequest.numberOfActions() > 0)
			runBulkRequest(bulkRequest, failed);
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	/**
	 * @return The local node
	 */
	public Node getNode() {
		return node;
	}

	/** Delete the index data and close the local node. */
	public void shutdown() {
		client.admin().indices().prepareDelete(TEST_INDEX).execute().actionGet();
		try {
			node.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void readTestData(File sampleDataRoot,
			BulkRequestBuilder bulkRequest) {
		Arrays.asList(sampleDataRoot.listFiles()).forEach((file) -> {
			try {
				String data = Files.readAllLines(Paths.get(file.getAbsolutePath()))
						.stream().map(String::trim).collect(Collectors.joining());
				boolean isItem = file.getName().contains(":");
				String type = isItem ? "item" : "resource";
				String id = file.getName();
				String parent = isItem
						? "http://lobid.org/resources/" + file.getName().split(":")[0]
						: "";
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

	private IndexRequestBuilder createRequestBuilder(final String type, String id,
			String parent, final Map<String, Object> map) {
		final IndicesAdminClient admin = client.admin().indices();
		if (!admin.prepareExists(TEST_INDEX).execute().actionGet().isExists()) {
			admin.prepareCreate(TEST_INDEX).setSource(config(), XContentType.JSON)
					.execute().actionGet();
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
