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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.openrdf.model.BNode;
import org.openrdf.model.Graph;
import org.openrdf.model.Statement;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jan Schnasse
 *
 */
public class JsonConverter {

	final static Logger logger = LoggerFactory.getLogger(JsonConverter.class);

	String first = "http://www.w3.org/1999/02/22-rdf-syntax-ns#first";
	String rest = "http://www.w3.org/1999/02/22-rdf-syntax-ns#rest";
	String nil = "http://www.w3.org/1999/02/22-rdf-syntax-ns#nil";
	String contributorOrder = "http://purl.org/lobid/lv#contributorOrder";
	String subjectOrder = "http://purl.org/lobid/lv#subjectOrder";

	List<Statement> all = new ArrayList<Statement>();

	/**
	 * You can easily convert the map to json using jackson
	 * (https://github.com/FasterXML/jackson) or other jaxb libs
	 * 
	 * @param in an input stream containing rdf data
	 * @param format the rdf format
	 * @param uri the uri from where the rdf ist taken. Can not be null.
	 * @return a map
	 */
	public Map<String, Object> convert(InputStream in, RDFFormat format,
			String uri) {
		Graph g = RdfUtils.readRdfToGraph(in, format, uri);
		collect(g);
		Map<String, Object> result = createMap(g, uri);
		result.put("@context", Globals.etikette.context.get("@context"));
		return result;
	}

	private Map<String, Object> createMap(Graph g, String uri) {
		Map<String, Object> jsonResult = new TreeMap<String, Object>();
		Iterator<Statement> i = g.iterator();
		jsonResult.put("@id", uri);
		while (i.hasNext()) {
			Statement s = i.next();
			Etikett e = Globals.etikette.getEtikett(s.getPredicate().stringValue());
			if (uri.equals(s.getSubject().stringValue())) {
				createObject(jsonResult, s, e);
			}
		}
		return jsonResult;
	}

	private void createObject(Map<String, Object> jsonResult, Statement s,
			Etikett e) {
		String key = e.name;
		if (s.getObject() instanceof org.openrdf.model.Literal) {
			addLiteralToJsonResult(jsonResult, key, s.getObject().stringValue());
		}
		if (s.getObject() instanceof org.openrdf.model.BNode) {
			if (contributorOrder.equals(s.getPredicate().stringValue())
					|| subjectOrder.equals(s.getPredicate().stringValue())) {
				addListToJsonResult(jsonResult, key, ((BNode) s.getObject()).getID());
			}
		} else {
			addObjectToJsonResult(jsonResult, key, s.getObject().stringValue());
		}
	}

	private void addListToJsonResult(Map<String, Object> jsonResult, String key,
			String id) {
		logger.info("Create list for " + key + " pointing to " + id);
		List<String> orderedList = new ArrayList<String>();
		for (Statement s : find(id)) {
			if (id.equals(s.getSubject().stringValue())) {
				if (first.equals(s.getPredicate().stringValue())) {
					logger.info("Find next data " + s.getObject().stringValue());
					orderedList.add(s.getObject().stringValue());
				} else if (rest.equals(s.getPredicate().stringValue())) {
					logger.info("Find next node " + s.getObject().stringValue());
					orderedList.addAll(traversList(s.getObject().stringValue()));
				} else {
					// In this case, it is an object
					// createObject(null);
				}
			}
		}
		jsonResult.put(key, orderedList);
	}

	private List<String> traversList(String uri) {
		List<String> orderedList = new ArrayList<String>();
		for (Statement s : find(uri)) {
			if (uri.equals(s.getSubject().stringValue())) {
				if (first.equals(s.getPredicate().stringValue())) {
					logger.info("Find next data " + s.getObject().stringValue());
					orderedList.add(s.getObject().stringValue());
				}
				if (rest.equals(s.getPredicate().stringValue())) {
					logger.info("Find next node " + s.getObject().stringValue());
					orderedList.addAll(traversList(s.getObject().stringValue()));
				}
			}
		}
		return orderedList;
	}

	private void addObjectToJsonResult(Map<String, Object> jsonResult, String key,
			String uri) {
		if (jsonResult.containsKey(key)) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> literals =
					(List<Map<String, Object>>) jsonResult.get(key);
			literals.add(createObject(uri));
		} else {
			List<Map<String, Object>> literals = new ArrayList<Map<String, Object>>();
			literals.add(createObject(uri));
			jsonResult.put(key, literals);
		}
	}

	private Map<String, Object> createObject(String uri) {
		Map<String, Object> newObject = new TreeMap<String, Object>();
		if (uri != null) {
			newObject.put("@id", uri);
			// newObject.put("prefLabel",
			// Globals.etikette.getEtikett(uri).label);
		}
		for (Statement s : find(uri)) {
			Etikett e = Globals.etikette.getEtikett(s.getPredicate().stringValue());
			if (s.getObject() instanceof org.openrdf.model.Literal) {
				newObject.put(e.name, s.getObject().stringValue());
			} else {
				createObject(newObject, s, e);
			}
		}
		return newObject;
	}

	private List<Statement> find(String uri) {
		List<Statement> result = new ArrayList<Statement>();
		for (Statement i : all) {
			if (uri.equals(i.getSubject().stringValue()))
				result.add(i);
		}
		return result;
	}

	private void addLiteralToJsonResult(Map<String, Object> jsonResult,
			String key, String value) {
		if (jsonResult.containsKey(key)) {
			@SuppressWarnings("unchecked")
			List<String> literals = (List<String>) jsonResult.get(key);
			literals.add(value);
		} else {
			List<String> literals = new ArrayList<String>();
			literals.add(value);
			jsonResult.put(key, literals);
		}
	}

	private void collect(Graph g) {
		Iterator<Statement> i = g.iterator();
		while (i.hasNext()) {
			Statement s = i.next();
			all.add(s);
		}
	}
}
