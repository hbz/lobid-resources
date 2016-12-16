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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.openrdf.model.BNode;
import org.openrdf.model.Graph;
import org.openrdf.model.Statement;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts ntriples to a map, ennhancing the data with data constructed via
 * {@link EtikettMaker}.
 * 
 * TODO: this class should either return a Json object or be renamed.
 * 
 * @author Jan Schnasse
 * @author Pascal Christoph (dr0i)
 */
public class JsonConverter {

	private String labelKey;
	private String idAlias;

	final static Logger logger = LoggerFactory.getLogger(JsonConverter.class);

	String first = "http://www.w3.org/1999/02/22-rdf-syntax-ns#first";
	String rest = "http://www.w3.org/1999/02/22-rdf-syntax-ns#rest";
	String nil = "http://www.w3.org/1999/02/22-rdf-syntax-ns#nil";
	private static final String RDF_TYPE =
			"http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

	private static ObjectMapper objectMapper = new ObjectMapper();
	Set<Statement> all = new HashSet<>();
	private String mainSubjectOfTheResource;

	private EtikettMakerInterface etikette;
	private Map<Statement, Statement> visited = new HashMap<>();

	/**
	 * @param e An EtikettMaker provides access to labels
	 */
	public JsonConverter(EtikettMakerInterface e) {
		etikette = e;
		labelKey = etikette.getLabelKey();
		idAlias = etikette.getIdAlias();
	}

	/**
	 * You can easily convert the map to json using the object mapper provided by
	 * {@link #getObjectMapper}.
	 * 
	 * @param in an input stream containing rdf data
	 * @param format the rdf format
	 * @param rootNodePrefix the prefix of the root subject node of the resource
	 * @param context to create valid json-ld you have to provide either a a map
	 *          containing a json-ld context or a url to a json-ldContext
	 * @return a map
	 */
	public Map<String, Object> convertLobidData(InputStream in, RDFFormat format,
			final String rootNodePrefix, Object context) {
		Graph g = RdfUtils.readRdfToGraph(in, format, "");
		String subject = g.parallelStream()
				.filter(triple -> triple.getPredicate().stringValue()
						.equals("http://www.w3.org/2007/05/powder-s#describedby"))
				.filter(triple -> triple.getSubject().stringValue()
						.startsWith(rootNodePrefix))
				.findFirst().get().getSubject().toString();
		return convert(subject, context, g);
	}

	/**
	 * You can easily convert the map to json using the object mapper provided by
	 * {@link #getObjectMapper}.
	 * 
	 * @param in an input stream containing rdf data
	 * @param format the rdf format
	 * @param subject the root subject node of the resource
	 * @param context to create valid json-ld you have to provide either a a map
	 *          containing a json-ld context or a url to a json-ldContext
	 * @return a map
	 */
	public Map<String, Object> convert(String subject, InputStream in,
			RDFFormat format, Object context) {
		Graph g = RdfUtils.readRdfToGraph(in, format, "");
		return convert(subject, context, g);
	}

	private Map<String, Object> convert(String subject, Object context, Graph g) {
		mainSubjectOfTheResource = subject;
		collect(g);
		Map<String, Object> result = createMap(g);
		result.put("@context", context);
		return result;
	}

	private Map<String, Object> createMap(Graph g) {
		Map<String, Object> jsonResult = new TreeMap<>();
		Iterator<Statement> i = g.iterator();
		jsonResult.put(idAlias, mainSubjectOfTheResource);
		while (i.hasNext()) {
			Statement s = i.next();
			if (mainSubjectOfTheResource.equals(s.getSubject().stringValue())) {
				Etikett e = etikette.getEtikett(s.getPredicate().stringValue());
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
		} else {
			if (s.getObject() instanceof org.openrdf.model.BNode) {
				if (isList(s)) {
					addListToJsonResult(jsonResult, key, ((BNode) s.getObject()).getID());
				} else {
					addBlankNodeToJsonResult(jsonResult, key,
							((BNode) s.getObject()).getID());
				}
			} else {
				if (s.getPredicate().stringValue().equals(RDF_TYPE)) {
					try {
						addLiteralToJsonResult(jsonResult, key,
								etikette.getEtikett(s.getObject().stringValue()).name);
					} catch (Exception ex) {
						logger.info("", ex);
					}
				} else {
					addObjectToJsonResult(jsonResult, key, s.getObject().stringValue());
				}
			}
		}
	}

	private boolean isList(Statement statement) {
		for (Statement s : find(statement.getObject().stringValue())) {
			if (first.equals(s.getPredicate().stringValue())) {
				return true;
			}
		}
		return false;
	}

	private void addListToJsonResult(Map<String, Object> jsonResult, String key,
			String id) {
		logger.debug("Create list for " + key + " pointing to " + id);
		jsonResult.put(key, traverseList(id, first, new ArrayList<>()));
	}

	/**
	 * The property "first" has always exactly one property "rest", which itself
	 * may point to a another "first" node. At the end of that chain the "rest"
	 * node has the value "nil".
	 * 
	 * @param uri
	 * @param property
	 * @param orderedList
	 * @return the ordered list
	 */
	private List<Object> traverseList(String uri, String property,
			List<Object> orderedList) {
		for (Statement s : find(uri)) {
			if (uri.equals(s.getSubject().stringValue())
					&& property.equals(s.getPredicate().stringValue())) {
				if (property.equals(first)) {
					if (s.getObject() instanceof URIImpl) {
						orderedList.add(createObjectWithId(s.getObject().stringValue()));
					} else if (s.getObject() instanceof BNode) {
						orderedList.add(createObjectWithoutId(s.getObject().stringValue()));
					} else
						orderedList.add(s.getObject().stringValue());
					traverseList(s.getSubject().stringValue(), rest, orderedList);
				} else if (property.equals(rest)) {
					traverseList(s.getObject().stringValue(), first, orderedList);
				}
			}
		}
		return orderedList;
	}

	private void addObjectToJsonResult(Map<String, Object> jsonResult, String key,
			String uri) {
		if (jsonResult.containsKey(key)) {
			if (jsonResult.get(key) instanceof ArrayList<?>) {
				@SuppressWarnings("unchecked")
				ArrayList<Map<String, Object>> literals =
						(ArrayList<Map<String, Object>>) jsonResult.get(key);
				literals.add(createObjectWithId(uri));
			} else {
				@SuppressWarnings("unchecked")
				Set<Map<String, Object>> literals =
						(Set<Map<String, Object>>) jsonResult.get(key);
				literals.add(createObjectWithId(uri));
			}
		} else {
			Set<Map<String, Object>> literals = new HashSet<>();
			literals.add(createObjectWithId(uri));
			jsonResult.put(key, literals);
		}
	}

	private void addBlankNodeToJsonResult(Map<String, Object> jsonResult,
			String key, String uri) {
		if (jsonResult.containsKey(key)) {
			@SuppressWarnings("unchecked")
			Set<Map<String, Object>> literals =
					(Set<Map<String, Object>>) jsonResult.get(key);
			literals.add(createObjectWithoutId(uri));
		} else {
			Set<Map<String, Object>> literals = new HashSet<>();
			literals.add(createObjectWithoutId(uri));
			jsonResult.put(key, literals);
		}
	}

	private Map<String, Object> createObjectWithId(String uri) {
		Map<String, Object> newObject = new TreeMap<>();
		if (uri != null) {
			newObject.put(idAlias, uri);
			if (etikette.supportsLabelsForValues()) {
				getLabelFromEtikettMaker(uri, newObject);
			}
			createObject(uri, newObject);
		}
		return newObject;
	}

	private void getLabelFromEtikettMaker(String uri,
			Map<String, Object> newObject) {
		try {
			String label = etikette.getEtikett(uri).label;
			if (label != null && !label.isEmpty()) {
				newObject.put(labelKey, label);
			}
		} catch (Exception e) {
			logger.debug(e.getMessage());
		}
	}

	private Map<String, Object> createObjectWithoutId(String uri) {
		Map<String, Object> newObject = new TreeMap<>();
		if (uri != null) {
			createObject(uri, newObject);
		}
		return newObject;
	}

	@SuppressWarnings("unchecked")
	private void createObject(String uri, Map<String, Object> newObject) {
		for (Statement s : find(uri)) {
			Etikett e = etikette.getEtikett(s.getPredicate().stringValue());
			if (s.getObject() instanceof org.openrdf.model.Literal) {
				if (newObject.containsKey(e.name)) {
					Object existingValue = newObject.get(e.name);
					if (existingValue instanceof String) {
						Set<String> icanmany = new HashSet<>();
						icanmany.add((String) existingValue);
						icanmany.add(s.getObject().stringValue());
						newObject.put(e.name, icanmany);
					} else {
						((Set<String>) existingValue).add(s.getObject().stringValue());
					}
				} else {
					newObject.put(e.name, s.getObject().stringValue());
				}
			} else {
				if (!mainSubjectOfTheResource.equals(s.getObject().stringValue())) {
					createObject(newObject, s, e);
				} else {
					if (!statementVisited(s)) {
						newObject.put(e.name, s.getObject().stringValue());
					}
				}
			}
		}
	}

	private boolean statementVisited(Statement s) {
		boolean result = visited.containsKey(s);
		if (result) {
			logger.debug("Already visited " + s);
		}
		visited.put(s, s);
		return result;
	}

	private Set<Statement> find(String uri) {
		Set<Statement> result = new HashSet<>();
		for (Statement i : all) {
			if (uri.equals(i.getSubject().stringValue()))
				result.add(i);
		}
		return result;
	}

	private static void addLiteralToJsonResult(
			final Map<String, Object> jsonResult, final String key,
			final String value) {
		if (jsonResult.containsKey(key)) {
			@SuppressWarnings("unchecked")
			Set<String> literals = (Set<String>) jsonResult.get(key);
			literals.add(value);
		} else {
			Set<String> literals = new HashSet<>();
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

	/**
	 * Convert the generated map to json using the {@link ObjectMapper}.
	 * 
	 * @return objectMapper for easy converting the map to json
	 */
	public static ObjectMapper getObjectMapper() {
		return objectMapper;
	}

}
