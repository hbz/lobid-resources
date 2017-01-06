package controllers.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;

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

	/**
	 * @param id The item ID
	 * @return The item JSON
	 */
	public static JsonNode getItem(String id) {
		Settings settings =
				Settings.settingsBuilder().put("cluster.name", "gaia-aither").build();
		try (Client client = TransportClient.builder().settings(settings).build()
				.addTransportAddress(new InetSocketTransportAddress(
						InetAddress.getByName("aither.hbz-nrw.de"), 9300))) {
			GetResponse response = client.prepareGet("resources", "item", id)
					.setParent(id.split(":")[0]).execute().actionGet();
			return Json.parse(response.getSourceAsString());
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return Json.newObject();
		}
	}

}
