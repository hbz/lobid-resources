/* Copyright 2018 Pascal Christoph, hbz. Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

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
	// the items will have their own index type and ES parents
	private static String keyToGetMainId = "hbzId";
	static String LOBID_DOMAIN = "http://lobid.org/";
	private static final String TYPE_ITEM = "item";
	private static final String TYPE_RESOURCE = "resource";
	private static AtomicInteger idCnt = new AtomicInteger(0);

	/**
	 * @param KEY_TO_GET_MAIN_ID the json-key to get the main elasticsearch ID
	 */
	public JsonLdItemSplitter2ElasticsearchJsonLd(
			final String KEY_TO_GET_MAIN_ID) {
		keyToGetMainId = KEY_TO_GET_MAIN_ID;
	}

	@Override
	public void process(final Map<String, Object> originModel) {
		extractItemFromResourceModel(originModel);
	}

	private void extractItemFromResourceModel(final Map<String, Object> jsonMap) {
		final String mainId = jsonMap.get(keyToGetMainId) != null
				? jsonMap.get(keyToGetMainId).toString()
				: String.valueOf(idCnt.incrementAndGet());
		@SuppressWarnings("unchecked")
		ArrayList<Map<String, Object>> hm =
				(ArrayList<Map<String, Object>>) jsonMap.get("hasItem");
		if (hm != null) {
			hm.forEach(i -> {
				try {
					getReceiver().process(treatItemAndAddInternalProperties(
							i.get("id").toString(), i, TYPE_ITEM, mainId));
				} catch (Exception e) {
					e.printStackTrace();
				}
				i.remove("itemOf");
				i.remove("describedBy");
				i.remove("@context");
			});
		}
		jsonMap.remove("hasItem.itemOf");
		try {
			getReceiver().process(addInternalProperties(mainId, jsonMap,
					TYPE_RESOURCE, new HashMap<>()));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static HashMap<String, String> treatItemAndAddInternalProperties(
			String id, final Map<String, Object> jsonMap, final String TYPE,
			final String MAIN_ID) {
		String idWithoutDomain =
				id.replaceAll(LOBID_DOMAIN + ".*/", "").replaceFirst("#!$", "");
		HashMap<String, String> jsonEsMap = new HashMap<>();
		jsonMap.put("@context", "http://lobid.org/resources/context.jsonld");
		jsonEsMap.put(ElasticsearchIndexer.Properties.PARENT.getName(), MAIN_ID);
		return addInternalProperties(idWithoutDomain, jsonMap, TYPE, jsonEsMap);
	}

	private static HashMap<String, String> addInternalProperties(String id,
			final Map<String, Object> jsonMap, final String TYPE,
			HashMap<String, String> jsonEsMap) {
		try {
			jsonEsMap.put(ElasticsearchIndexer.Properties.GRAPH.getName(),
					JsonUtils.toString(jsonMap));
		} catch (IOException e) {
			e.printStackTrace();
		}
		jsonEsMap.put(ElasticsearchIndexer.Properties.TYPE.getName(), TYPE);
		jsonEsMap.put(ElasticsearchIndexer.Properties.ID.getName(), id);
		return jsonEsMap;
	}
}
