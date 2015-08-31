/* Copyright 2015  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.lobid.resources;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.junit.Test;

/**
 * Reads a directory with records got from lobid.org in ntriple serialization.
 * The records are indexed as JSON-LD in a living instance, waiting to be played
 * with by whomever.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class LobidResources2ElasticsearchLobidTestOnline {

	private static TransportClient transportClient;

	@SuppressWarnings("static-method")
	@Test
	public void testOnline() {
		transportClient = new TransportClient(
				ImmutableSettings.settingsBuilder().put("cluster.name", "weywot"));
		LobidResources2ElasticsearchLobidTest
				.buildAndExecuteFlow(transportClient.addTransportAddress(
						new InetSocketTransportAddress("weywot2.hbz-nrw.de", 9300)));
	}
}
