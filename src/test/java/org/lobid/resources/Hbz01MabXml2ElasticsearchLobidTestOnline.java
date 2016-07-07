/* Copyright 2015  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.lobid.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
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
	private final String CLUSTER_NAME = "quaoar1";
	private final String HOSTNAME = "quaoar1.hbz-nrw.de";

	@Test
	public void testOnline() {
		Settings settings =
				Settings.settingsBuilder().put("cluster.name", CLUSTER_NAME).build();
		tc = TransportClient.builder().settings(settings).build();
		try {
			Hbz01MabXml2ElasticsearchLobidTest.etl(
					tc.addTransportAddress(new InetSocketTransportAddress(
							InetAddress.getByName(HOSTNAME), 9300)),
					new RdfModel2ElasticsearchEtikettJsonLd(
							AbstractIngestTests.LOBID_JSONLD_CONTEXT));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		Hbz01MabXml2ElasticsearchLobidTest
				.getElasticsearchDocsAsNtriplesAndTestAndWrite();
	}
}
