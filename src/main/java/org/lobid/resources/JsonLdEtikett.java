/* Copyright 2018 Pascal Christoph, hbz. Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

import de.hbz.lobid.helper.EtikettMaker;
import de.hbz.lobid.helper.EtikettMakerInterface;

/**
 * Enrich a JSON-LD map document with etikett.
 * 
 * @author Pascal Christoph (dr0i)
 */
@In(Map.class)
@Out(HashMap.class)
public final class JsonLdEtikett extends
		DefaultObjectPipe<Map<String, Object>, ObjectReceiver<Map<String, Object>>> {
	private static final Logger LOG = LogManager.getLogger(JsonLdEtikett.class);
	private Object jsonLdContext;
	private String labelsDirectoryName = "labels";
	private EtikettMakerInterface etikettMaker;

	/**
	 * Provides default constructor. Every json ld document gets the whole json ld
	 * context defined in in the default @see{labelsDirectoryName} which is then
	 * constructed via @see{EtikettMaker};
	 */
	public JsonLdEtikett() {
		this("default");
	}

	/**
	 * Provides a json ld context. May be a json string or a http URI as string.
	 * If its an URI the URI will be the value of the @context-field. If it's a
	 * whole json string, the whole string is added under the @context field:
	 * 
	 * @param jsonLdContext May be a json as string or a http uri as string.
	 */
	public JsonLdEtikett(final Object jsonLdContext) {
		setup(jsonLdContext);
	}

	/**
	 * Takes a filename which could be a directory to create the context jsonld
	 * out of labels. Second parameter is the value of the jsonld @context field.
	 * 
	 * @param fn the name of the file
	 * @param jsonContextLd the content of the @context field of the jsonld
	 */
	public JsonLdEtikett(final File fn, final String jsonContextLd) {
		labelsDirectoryName = fn.getPath();
		LOG.info("use labels directory: " + labelsDirectoryName);
		setup(jsonContextLd);
	}

	private void setup(final Object jsonLdContext1) {
		etikettMaker =
				new EtikettMaker(new File(Thread.currentThread().getContextClassLoader()
						.getResource(getLabelsDirectoryName()).getFile()));
		if (jsonLdContext1.equals("default")) {
			LOG.info("Adding json ld context to every document");
			jsonLdContext = etikettMaker.getContext().get("@context");
		} else
			if (jsonLdContext1.toString().substring(0, 4).equalsIgnoreCase("http")) {
			jsonLdContext = jsonLdContext1.toString();
			LOG.info("Using context URI: " + jsonLdContext);
		}
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
		// don't label the root id itself
		Object rootId = jsonMap.remove("id");
		getAllJsonNodes(jsonMap);
		jsonMap.put("id", rootId);
		return jsonMap;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Map<String, Object> getAllJsonNodes(Map<String, Object> jsonMap) {
		Iterator<String> it = jsonMap.keySet().iterator();
		boolean hasId = false;
		boolean hasLabel = false;
		while (it.hasNext()) {
			String key = it.next();
			if (key.equals("label")) {
				hasLabel = true;
			} else if (!hasLabel && key.equals("id"))
				hasId = true;
			if (jsonMap.get(key) instanceof ArrayList) {
				((ArrayList) jsonMap.get(key))//
						.stream().filter(e -> (e instanceof LinkedHashMap))
						.forEach(e -> getAllJsonNodes((Map<String, Object>) e));
			} else if (jsonMap.get(key) instanceof LinkedHashMap) {
				getAllJsonNodes((Map<String, Object>) jsonMap.get(key));
			}
		}
		if (hasId && !(hasLabel)) {
			jsonMap.put("label", "pchbz");
		}
		return jsonMap;
	}

	/**
	 * Gets the name of the directory of the label(s). Will be used to create
	 * jsonld-context.
	 * 
	 * @return the directory name to the label(s)
	 */
	public String getLabelsDirectoryName() {
		return labelsDirectoryName;
	}

	/**
	 * Gets the value of the @context field in the jsonld.
	 * 
	 * @return the content of the @context field of the jsonld.
	 */
	public String getJsonLdContext() {
		return jsonLdContext.toString();
	}

	/**
	 * @return filename of the jsonld-context
	 */
	public String getContextLocation() {
		return etikettMaker.getContextLocation();
	}

	/**
	 * @param contextLocation the location of the context
	 */
	public void setContextLocation(final String contextLocation) {
		etikettMaker.setContextLocation(contextLocation);
	}
}
