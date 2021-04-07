/* Copyright 2017, hbz. Licensed under the EPL 2.0 */

import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import controllers.resources.LocalIndex;
import controllers.resources.Search;
import controllers.resources.Webhook;
import play.Application;
import play.GlobalSettings;
import play.Logger;

/**
 * Application global settings.
 * 
 * See https://www.playframework.com/documentation/2.4.x/JavaGlobal
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class Global extends GlobalSettings {

	/** The cluster hosts as configred in resources.conf */
	private static final List<String> CLUSTER_HOSTS =
			controllers.resources.Application.CONFIG.getList("index.cluster.hosts")
					.stream().map(v -> v.unwrapped().toString())
					.collect(Collectors.toList());
	private static final int CLUSTER_PORT =
			controllers.resources.Application.CONFIG.getInt("index.cluster.port");
	private static final String CLUSTER_NAME =
			controllers.resources.Application.CONFIG.getString("index.cluster.name");

	private LocalIndex localIndex = null;
	private Client client = null;

	@Override
	public void onStart(Application app) {
		super.onStart(app);
		if (CLUSTER_HOSTS.isEmpty() && !app.isTest()) {
			localIndex = new LocalIndex();
			client = localIndex.getNode().client();
		} else if (!app.isTest()) {
			Settings settings =
					Settings.builder().put("cluster.name", CLUSTER_NAME).build();
			TransportClient c = new PreBuiltTransportClient(settings);
			addHosts(c);
			client = c;
			Webhook.clusterHost = CLUSTER_HOSTS.get(0);
			Webhook.clusterName = CLUSTER_NAME;
		}
		if (client != null) {
			Search.elasticsearchClient = client;
		}
	}

	@Override
	public void onStop(Application app) {
		if (localIndex != null) {
			localIndex.shutdown();
		}
		if (client != null) {
			client.close();
		}
		super.onStop(app);
	}

	private static void addHosts(TransportClient client) {
		for (String host : CLUSTER_HOSTS) {
			try {
				client.addTransportAddress(new InetSocketTransportAddress(
						InetAddress.getByName(host), CLUSTER_PORT));
			} catch (Exception e) {
				Logger.warn("Could not add host {} to Elasticsearch client: {}", host,
						e.getMessage());
			}
		}
	}
}
