/* Copyright 2019 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources.run;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.lobid.resources.ElasticsearchIndexer;

/**
 * Updates the index aliases "[resources|geo_nwbib]-staging" to
 * "[resources|geo_nwbib]" and vice versa. I.e. making the staging index
 * productive and the productive index to be stage.
 * 
 * @author Pascal Christoph (dr0i)
 *
 */
public class UpdateAliases {

	/**
	 * @param args ignored
	 */
	public static void main(String... args) {
		try (
				TransportClient tc = new PreBuiltTransportClient(
						Settings.builder().put("cluster.name", "weywot").build());
				Client client = tc.addTransportAddress(new InetSocketTransportAddress(
						InetAddress.getByName("weywot4.hbz-nrw.de"), 9300))) {
			ElasticsearchIndexer esIndexer = new ElasticsearchIndexer();
			esIndexer.setElasticsearchClient(client);
			esIndexer.swapProductionAndStagingAliases("resources",
					"resources-staging");
			esIndexer.swapProductionAndStagingAliases("geo_nwbib",
					"geo_nwbib-staging");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

	}
}
