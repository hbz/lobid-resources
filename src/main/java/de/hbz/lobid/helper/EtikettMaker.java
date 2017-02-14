/*Copyright (c) 2015,2016 "hbz"

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeBase;

/**
 * @author Jan Schnasse
 * @author Pascal Christoph (dr0i)
 *
 */
public class EtikettMaker implements EtikettMakerInterface {

	private static final String TYPE = "type";

	private static final String ID = "id";

	final static Logger logger = LoggerFactory.getLogger(EtikettMaker.class);

	/**
	 * A map with URIs as key
	 */
	Map<String, Etikett> pMap = new HashMap<>();

	/**
	 * A map with Shortnames as key
	 */
	Map<String, Etikett> nMap = new HashMap<>();

	/**
	 * The context will be loaded on startup. You can reload the context with POST
	 * /utils/reloadContext
	 * 
	 */
	Map<String, Object> context = new HashMap<>();

	/**
	 * The labels will be loaded on startup. You can reload the context with POST
	 * /utils/reloadLabels
	 * 
	 */
	List<Etikett> labels = new ArrayList<>();

	/**
	 * The profile provides a json context and labels
	 * 
	 * @param labelIn input stream to a labels file
	 */
	public EtikettMaker(InputStream labelIn) {
		this(new InputStream[] { labelIn });
	}

	/**
	 * The profile provides a json context and labels
	 * 
	 * @param labelInArr input stream array to label(s) file(s)
	 */
	public EtikettMaker(InputStream[] labelInArr) {
		initMaps(labelInArr);
		initContext();
	}

	/**
	 * The file provides a json context and labels. If it's one file this is the
	 * labels. If fil is a drirectory every file in it will be merged to one
	 * labels.
	 * 
	 * @param labelFile a file to the label(s)
	 */
	public EtikettMaker(File labelFile) {
		this(getInputStreamArray(labelFile));
	}

	private static InputStream[] getInputStreamArray(File labelFile) {
		InputStream[] is = null;
		try {
			if (labelFile.isDirectory()) {
				File farr[] = labelFile.listFiles();
				is = new InputStream[farr.length];
				for (int i = 0; i < farr.length; i++) {
					is[i] = new FileInputStream(farr[i]);
				}
			} else {
				try (FileInputStream fis = new FileInputStream(labelFile)) {
					is = new FileInputStream[] { fis };
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return is;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.hbz.lobid.helper.EtikettMakerInterface#getContext()
	 */
	@Override
	public Map<String, Object> getContext() {
		return context;
	}

	/*
	 * Trying to get a label as sidecar for URIs. First fallback: If an etikett is
	 * configured but "label" is missing the "name" will be taken. Second
	 * fallback: the domainname (and a possible path) will be extracted from the
	 * URI. This domainname is lookuped in the labels. Last fallback: If there is
	 * nothing found, the domainname itself will be the label.
	 * 
	 * In the end there will be a label for every URI.
	 * 
	 * @see de.hbz.lobid.helper.EtikettMakerInterface#getEtikett(java.lang.String)
	 */
	@Override
	public Etikett getEtikett(String uri) {
		Etikett e = pMap.get(uri);
		if (e == null) {
			e = new Etikett(uri);
			try {
				e.name = getJsonName(uri);
			} catch (Exception ex) { // fallback domainname
				logger.debug(
						"no json name available. Please provide a labels.json file with proper 'name' entry. Using domainname as fallback.");
				String[] uriparts = uri.split("/");
				String domainname =
						uriparts[0] + "/" + uriparts[1] + "/" + uriparts[2] + "/";
				e = pMap
						.get(uriparts.length > 3 ? domainname + uriparts[3] : domainname);
				if (e == null) { // domainname may have a label
					e = new Etikett(uri);
					try {
						e.name = getJsonName(uri);
					} catch (Exception exc) {
						e.label =
								uriparts.length > 3 ? domainname + uriparts[3] : domainname;
					}
				}
			}
		}
		if (e.label == null || e.label.isEmpty()) { // fallback name
			e.label = e.name;
		}
		logger.debug("Etikett for " + uri + " : " + e.label);
		return e;
	}

	private void initContext() {
		context = createContext();
	}

	/**
	 * Generates context.json based on labels.json. Stores into filesystem.
	 * 
	 */
	public void writeContext() {
		logger.info("Writing context file ...");
		try {
			JsonConverter.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
					.writeValue(new File("src/main/resources/context.json"), context);
			logger.info("... done writing context file.");
		} catch (Exception e) {
			logger.error("Error during writing context file! ", e);
		}
	}

	private void initMaps(InputStream[] labelInArr) {
		try {
			labels = createLabels(labelInArr);
			for (Etikett etikett : labels) {
				pMap.put(etikett.uri, etikett);
				nMap.put(etikett.name, etikett);
			}
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	private static List<Etikett> createLabels(InputStream[] labelInArr) {
		logger.info("Create labels....");
		String msg = "...succeed!";
		List<Etikett> result = new ArrayList<>();
		try {
			for (InputStream is : labelInArr) {
				result.addAll(loadFile(is, new ObjectMapper().getTypeFactory()
						.constructCollectionType(List.class, Etikett.class)));
			}
		} catch (Exception e) {
			msg = "...not succeeded!";
		}
		logger.info(msg);
		return result;
	}

	Map<String, Object> createContext() {
		Map<String, Object> pmap;
		Map<String, Object> cmap = new HashMap<>();
		for (Etikett l : labels) {
			if ("class".equals(l.referenceType) || l.referenceType == null
					|| l.name == null)
				continue;
			pmap = new HashMap<>();
			pmap.put("@id", l.uri);
			if (!"String".equals(l.referenceType)) {
				pmap.put("@type", l.referenceType);
			}
			if (l.container != null) {
				pmap.put("@container", l.container);
			}
			cmap.put(l.name, pmap);
		}
		cmap.put(ID, "@id");
		cmap.put(TYPE, "@type");
		Map<String, Object> contextObject = new HashMap<>();
		contextObject.put("@context", cmap);
		return contextObject;
	}

	private static <T> T loadFile(InputStream labelIn, TypeBase type) {
		try (InputStream in = labelIn) {
			return new ObjectMapper().readValue(in, type);
		} catch (Exception e) {
			throw new RuntimeException("Error during initialization!", e);
		}
	}

	/**
	 * @param predicate
	 * @return The short name of the predicate uses String.split on first index of
	 *         '#' or last index of '/'
	 */
	String getJsonName(String predicate) {
		return pMap.get(predicate).name;
	}

	@Override
	public Etikett getEtikettByName(String name) {
		return nMap.get(name);
	}

	@Override
	public Collection<Etikett> getValues() {
		return pMap.values();
	}

	@Override
	public boolean supportsLabelsForValues() {
		return true;
	}

	@Override
	public String getIdAlias() {
		return ID;
	}

	@Override
	public String getTypeAlias() {
		return TYPE;
	}

	@Override
	public String getLabelKey() {
		return "label";
	}

}
