/* Copyright 2015  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.lobid.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.Test;

/**
 * Makes use of {@link Hbz01MabXml2ElasticsearchLobidTest} to extract, transform
 * and load data. The records are indexed as JSON-LD in a living instance.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
@SuppressWarnings("javadoc")
public final class Hbz01MabXml2ElasticsearchLobidTestOnline {
	private TransportClient tc;
	private final String CLUSTER_NAME = "weywot";
	private final String HOSTNAME = "weywot4.hbz-nrw.de";

	@Test
	public void testOnline() {
		Settings settings =
				Settings.builder().put("cluster.name", CLUSTER_NAME).build();
		this.tc = new PreBuiltTransportClient(settings);
		try {
			Hbz01MabXml2ElasticsearchLobidTest.etl(
					tc.addTransportAddress(new InetSocketTransportAddress(
							InetAddress.getByName(HOSTNAME), 9300)),
					new JsonLdItemSplitter2ElasticsearchJsonLd("hbzId"));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		Hbz01MabXml2ElasticsearchLobidTest
				.getElasticsearchDocsAsNtriplesAndTestAndWrite();
	}
}
