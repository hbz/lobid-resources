/* Copyright 2015  hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.lobid.resources;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
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

	private static final TransportClient TRANSPORT_CLIENT = new TransportClient(
			ImmutableSettings.settingsBuilder().put("cluster.name", "lobid-gaia"));

	@SuppressWarnings("static-method")
	@Test
	public void testOnline() {
		Hbz01MabXml2ElasticsearchLobidTest.etl(
				TRANSPORT_CLIENT.addTransportAddress(
						new InetSocketTransportAddress("gaia.hbz-nrw.de", 9300)),
				new RdfModel2ElasticsearchEtikettJsonLd(
						AbstractIngestTests.LOBID_JSONLD_CONTEXT));
		Hbz01MabXml2ElasticsearchLobidTest
				.getElasticsearchDocsAsNtriplesAndTestAndWrite();
	}
}
