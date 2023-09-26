/* Copyright 2017, hbz. Licensed under the EPL 2.0 */

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.resources.Lobid;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import controllers.resources.LocalIndex;
import controllers.resources.Search;
import controllers.resources.WebhookAlmaFix;

import org.lobid.resources.run.AlmaMarcXmlFix2lobidJsonEs;
import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.libs.Json;

/**
 * Application global settings.
 * <p>
 * See https://www.playframework.com/documentation/2.4.x/JavaGlobal
 *
 * @author Fabian Steeg (fsteeg)
 */
public class Global extends GlobalSettings {

    /**
     * The cluster hosts as configured in resources.conf
     */
    private static final List<String> CLUSTER_HOSTS =
        controllers.resources.Application.CONFIG.getList("index.cluster.hosts")
            .stream().map(v -> v.unwrapped().toString())
            .collect(Collectors.toList());
    private static final int CLUSTER_PORT =
        controllers.resources.Application.CONFIG.getInt("index.cluster.port");
    private static final String CLUSTER_NAME =
        controllers.resources.Application.CONFIG.getString("index.cluster.name");
    private static final String MAILTO_INFO =
        controllers.resources.Application.CONFIG.getString("webhook.mailtoInfo");
    private static final String MAILTO_ERROR =
        controllers.resources.Application.CONFIG.getString("webhook.mailtoError");
    private static final String TRIGGER_WEBHOOK_URL = controllers.resources.Application.CONFIG.getString("webhook.triggerWebhook.url");
    private static final String TRIGGER_WEBHOOK_DATA = controllers.resources.Application.CONFIG.getString("webhook.triggerWebhook.data");
    private LocalIndex localIndex = null;
    private Client client = null;

    private static final String ISIL2OPAC ="../link-templates/isil2opac_hbzid.json";

    @Override
    public void onStart(Application app) {
        super.onStart(app);
        if (CLUSTER_HOSTS.isEmpty() && !app.isTest()) {
            localIndex = new LocalIndex();
            client = localIndex.getNode().client();
        }
        else if (!app.isTest()) {
            Settings settings =
                Settings.builder().put("cluster.name", CLUSTER_NAME).build();
            TransportClient c = new PreBuiltTransportClient(settings);
            addHosts(c);
            client = c;
            WebhookAlmaFix.clusterHost = CLUSTER_HOSTS.get(0);
            WebhookAlmaFix.clusterName = CLUSTER_NAME;
            AlmaMarcXmlFix2lobidJsonEs.setMailtoInfo(MAILTO_INFO);
            AlmaMarcXmlFix2lobidJsonEs.setMailtoError(MAILTO_ERROR);
            WebhookAlmaFix.triggerWebhookUrl = TRIGGER_WEBHOOK_URL;
            WebhookAlmaFix.triggerWebhookData = TRIGGER_WEBHOOK_DATA;
        }
        if (client != null) {
            Search.elasticsearchClient = client;
        }
        Lobid.isil2opac = loadIsil2Opac();
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
            }
            catch (Exception e) {
                Logger.warn("Could not add host {} to Elasticsearch client: {}", host,
                    e.getMessage());
            }
        }
    }

    private static JsonNode loadIsil2Opac() {
        JsonNode node = null;
        try (InputStream stream =
                 new FileInputStream(ISIL2OPAC)) {
            node = Json.parse(stream);
        }
        catch (IOException e) {
            Logger.error("Could not create OPAC URL", e);
        }
        return node;
    }
}
