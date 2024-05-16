/* Copyright 2018 Pascal Christoph, hbz. Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.metafacture.framework.ObjectReceiver;
import org.metafacture.framework.annotations.In;
import org.metafacture.framework.annotations.Out;
import org.metafacture.framework.helpers.DefaultObjectPipe;

import de.hbz.lobid.helper.EtikettMaker;

/**
 * Enrich a JSON-LD map document with etikett.
 *
 * @author Pascal Christoph (dr0i)
 */
@In(Map.class)
@Out(HashMap.class)
public final class JsonLdEtikett extends
		DefaultObjectPipe<Map<String, Object>, ObjectReceiver<Map<String, Object>>> {
	private static final Logger LOG = LoggerFactory.getLogger(JsonLdEtikett.class);
	private static String labelsDirectoryName = "labels";
	private static String contextFilenameLocation = "web/conf/context.jsonld";

	private EtikettMaker etikettMaker;

	/**
	 * Provides default constructor. Every json ld document gets the whole json ld
	 * context constructed via @see{EtikettMaker} out of the
	 * default @see{labelsDirectoryName.
	 */
	public JsonLdEtikett() {
		this(labelsDirectoryName, contextFilenameLocation);
	}

	/**
	 * Takes a filename which could be a directory to create the context jsonld
	 * out of labels.
	 *
	 * @param LABELS_DIRECTORY_FILENAME the name of the labels firectory or file
	 * @param CONTEXT_LOCATION_FILENAME the filename of the to be produced and to
	 *          be stored context
	 */
	public JsonLdEtikett(final String LABELS_DIRECTORY_FILENAME,
			final String CONTEXT_LOCATION_FILENAME) {
		labelsDirectoryName = LABELS_DIRECTORY_FILENAME;
		etikettMaker =
				new EtikettMaker(new File(Thread.currentThread().getContextClassLoader()
						.getResource(LABELS_DIRECTORY_FILENAME).getFile()));
		etikettMaker.setContextLocation(CONTEXT_LOCATION_FILENAME);
		etikettMaker.writeContext();
	}

	@Override
	public void process(final Map<String, Object> jsonMap) {
		if (jsonMap.get("id") != null)
			getReceiver().process(getEtikettForEveryUri(jsonMap));
		else
			LOG.warn("jsonMap without id, ignoring");
	}

	private Map<String, Object> getEtikettForEveryUri(
			final Map<String, Object> jsonMap) {
		// don't label the root id
		Object rootId = jsonMap.remove("id");
		getAllJsonNodes(jsonMap);
		jsonMap.put("id", rootId);
		return jsonMap;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map<String, Object> getAllJsonNodes(Map<String, Object> jsonMap) {
		Iterator<String> it = jsonMap.keySet().iterator();
		boolean hasLabel = false;
		String id = null;
		while (it.hasNext()) {
			String key = it.next();
			if (key.equals("label"))
				hasLabel = true;
			else if (!hasLabel && key.equals("id"))
				id = (String) jsonMap.get(key);
			if (jsonMap.get(key) instanceof ArrayList)
				((ArrayList) jsonMap.get(key))//
						.stream().filter(e -> (e instanceof LinkedHashMap))
						.forEach(e -> getAllJsonNodes((Map<String, Object>) e));
			else if (jsonMap.get(key) instanceof LinkedHashMap)
				getAllJsonNodes((Map<String, Object>) jsonMap.get(key));
		}
		if (id != null && !(hasLabel))
			jsonMap.put("label", etikettMaker.getEtikett(id).label);
		return jsonMap;
	}

	/**
	 * Gets the name of the directory of the label(s). Will be used to create
	 * jsonld-context.
	 *
	 * @return the directory name to the label(s)
	 */
	public static String getLabelsDirectoryName() {
		return labelsDirectoryName;
	}

	/**
	 * Gets the filename of the context.
	 *
	 * @return the filename to the context
	 */
	public String getContextName() {
		return etikettMaker.getContextLocation();
	}
}
