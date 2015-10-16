/*Copyright (c) 2015 "hbz"

This file is part of lobid-rdf-to-json.

etikett is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.hbz.lobid.helper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeBase;

/**
 * @author Jan Schnasse
 *
 */
public class EtikettMaker {

    final static Logger logger = LoggerFactory.getLogger(EtikettMaker.class);

    /**
     * A map with URIs as key and labels,icons, shortnames as values
     */
    Map<String, Etikett> pMap = new HashMap<String, Etikett>();

    /**
     * A map with Shortnames as key and labels,icons, uris as values
     */
    Map<String, Etikett> nMap = new HashMap<String, Etikett>();

    /**
     * The context will be loaded on startup. You can reload the context with
     * POST /utils/reloadContext
     * 
     */
    Map<String, Object> context = new HashMap<String, Object>();

    /**
     * The labels will be loaded on startup. You can reload the context with
     * POST /utils/reloadLabels
     * 
     */
    List<Etikett> labels = new ArrayList<Etikett>();

    /**
     * The profile provides a json context an labels
     */
    public EtikettMaker() {
	initContext();
	initMaps();
    }

    /**
     * @return a map with a json-ld context
     */
    public Map<String, Object> getContext() {
	return context;
    }

    /**
     * @param key
     *            the uri
     * @return an etikett object contains uri, icon, label, jsonname,
     *         referenceType
     */
    public Etikett getEtikett(String key) {
	Etikett e = pMap.get(key);
	if (e == null) {
	    e = new Etikett(key);
	    e.name = getJsonName(key);
	}
	if (e.label == null) {
	    e.label = e.uri;
	}
	logger.debug("Find name for " + key + " : " + e.name);
	return e;
    }

    private void initContext() {
	context = createContext("context.json");
    }

    private void initMaps() {
	try {
	    labels = createLabels("labels.json");

	    for (Etikett etikett : labels) {
		pMap.put(etikett.uri, etikett);
		nMap.put(etikett.name, etikett);
	    }
	} catch (Exception e) {
	    logger.debug("", e);
	}

    }

    private List<Etikett> createLabels(String fileName) {
	logger.info("Create labels....");
	List<Etikett> result = new ArrayList<Etikett>();

	result = loadFile(fileName, new ObjectMapper().getTypeFactory()
		.constructCollectionType(List.class, Etikett.class));

	if (result == null) {
	    logger.info("...not succeeded!");
	} else {
	    logger.info("...succeed!");
	}
	return result;
    }

    /**
     * @return a Map representing additional information about the shortnames
     *         used in getLd
     */
    Map<String, Object> createContext(String fileName) {
	logger.info("Create context....");
	Map<String, Object> result = new HashMap<String, Object>();

	result = loadFile(
		fileName,
		new ObjectMapper().getTypeFactory().constructMapLikeType(
			HashMap.class, String.class, Object.class));

	if (result == null) {
	    logger.info("...not succeeded!");
	} else {
	    logger.info("...succeed!");
	}
	return result;
    }

    private <T> T loadFile(String fileName, TypeBase type) {
	try (InputStream in = Thread.currentThread().getContextClassLoader()
		.getResourceAsStream(fileName)) {
	    return new ObjectMapper().readValue(in, type);
	} catch (Exception e) {
	    throw new RuntimeException("Error during initialization!", e);
	}
    }

    /**
     * @param predicate
     * @return The short name of the predicate uses String.split on first index
     *         of '#' or last index of '/'
     */
    String getJsonName(String predicate) {
	String result = null;
	Etikett e = pMap.get(predicate);
	if (e != null) {
	    result = e.name;
	}
	if (result == null || result.isEmpty()) {
	    String prefix = "";
	    if (predicate.startsWith("http://purl.org/dc/elements"))
		prefix = "dc:";
	    if (predicate.contains("#"))
		return prefix + predicate.split("#")[1];
	    else if (predicate.startsWith("http")) {
		int i = predicate.lastIndexOf("/");
		return prefix + predicate.substring(i + 1);
	    }
	    result = prefix + predicate;
	}
	return result;
    }
}
