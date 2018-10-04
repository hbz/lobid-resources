/* Copyright 2018 Pascal Christoph, hbz. Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.utils.JsonUtils;

/**
 * Splits a lobid resource into items and make them consumable by elasticsearch.
 * 
 * @author Pascal Christoph (dr0i)
 */
@In(HashMap.class)
@Out(HashMap.class)
public final class JsonLdItemSplitter2ElasticsearchJsonLd extends
		DefaultObjectPipe<Map<String, Object>, ObjectReceiver<HashMap<String, String>>> {
	private static final Logger LOG =
			LogManager.getLogger(JsonLdItemSplitter2ElasticsearchJsonLd.class);
	// the items will have their own index type and ES parents
	private static final String PROPERTY_TO_PARENT = "itemOf";
	static String LOBID_DOMAIN = "http://lobid.org/";
	// the sub node we want to create besides the main node
	private static String LOBID_ITEM_URI_PREFIX = LOBID_DOMAIN + "items/";
	private static final String TYPE_ITEM = "item";
	private static final String TYPE_RESOURCE = "resource";

	@Override
	public void process(final Map<String, Object> originModel) {
		extractItemFromResourceModel(originModel);
	}

	private void extractItemFromResourceModel(final Map<String, Object> jsonMap) {
		@SuppressWarnings("unchecked")
		ArrayList<Map<String, Object>> hm =
				(ArrayList<Map<String, Object>>) jsonMap.get("hasItem");
		if (hm != null) {
			hm.forEach(i -> {
				try {
					getReceiver().process(addInternalProperties(i.get("id").toString(),
							JsonUtils.toString(i)));
				} catch (Exception e) {
					e.printStackTrace();
				}
				i.remove("itemOf");
				i.remove("describedBy");
			});
		}
		jsonMap.remove("hasItem.itemOf");
		try {
			getReceiver().process(addInternalProperties(
					jsonMap.get("hbzId").toString(), JsonUtils.toString(jsonMap)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static HashMap<String, String> addInternalProperties(String id,
			String json) {
		String type = TYPE_RESOURCE;
		String idWithoutDomain =
				id.replaceAll(LOBID_DOMAIN + ".*/", "").replaceFirst("#!$", "");
		HashMap<String, String> jsonMap = new HashMap<>();
		if (id.startsWith(LOBID_ITEM_URI_PREFIX)) {
			type = TYPE_ITEM;
			try {
				JsonNode node = new ObjectMapper().readValue(json, JsonNode.class);
				final JsonNode parent = node.findPath(PROPERTY_TO_PARENT);
				String p = parent.asText().replaceAll(LOBID_DOMAIN + ".*/", "")
						.replaceFirst("#!$", "");
				if (p.isEmpty()) {
					LOG.warn("Item " + idWithoutDomain + " has no parent declared!");
					jsonMap.put(ElasticsearchIndexer.Properties.PARENT.getName(),
							"no_parent");
				} else
					jsonMap.put(ElasticsearchIndexer.Properties.PARENT.getName(), p);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		jsonMap.put(ElasticsearchIndexer.Properties.GRAPH.getName(), json);
		jsonMap.put(ElasticsearchIndexer.Properties.TYPE.getName(), type);
		jsonMap.put(ElasticsearchIndexer.Properties.ID.getName(), idWithoutDomain);
		return jsonMap;
	}
}
