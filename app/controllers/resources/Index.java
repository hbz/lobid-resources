package controllers.resources;

import java.net.InetAddress;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import com.fasterxml.jackson.databind.JsonNode;

import play.libs.Json;

/**
 * Access to the Elasticsearch index.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Index {

	private static final String INDEX_NAME =
			Application.CONFIG.getString("index.name");
	private static final String TYPE_ITEM =
			Application.CONFIG.getString("index.type.item");
	private static final int CLUSTER_PORT =
			Application.CONFIG.getInt("index.cluster.port");
	private static final String CLUSTER_HOST =
			Application.CONFIG.getString("index.cluster.host");
	private static final String CLUSTER_NAME =
			Application.CONFIG.getString("index.cluster.name");

	/**
	 * @param id The item ID
	 * @return The item JSON
	 */
	public static JsonNode getItem(String id) {

		Settings settings =
				Settings.settingsBuilder().put("cluster.name", CLUSTER_NAME).build();
		try (Client client = TransportClient.builder().settings(settings).build()
				.addTransportAddress(new InetSocketTransportAddress(
						InetAddress.getByName(CLUSTER_HOST), CLUSTER_PORT))) {
			GetResponse response = client.prepareGet(INDEX_NAME, TYPE_ITEM, id)
					.setParent(id.split(":")[0]).execute().actionGet();
			return Json.parse(response.getSourceAsString());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

}
