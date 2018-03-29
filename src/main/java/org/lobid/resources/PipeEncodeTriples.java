/* Copyright 2013 Fabian Steeg, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.framework.StreamReceiver;
import org.culturegraph.mf.framework.annotations.Description;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.rdf.model.AnonId;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFList;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.util.ResourceUtils;

/**
 * Treats Literals, URIs, Blank Nodes and Lists. The latter will be invoked by
 * using the <entity> element in the morph file. Output are N-Triples.
 * 
 * @author Fabian Steeg, Pascal Christoph (dr0i)
 */
@Description("Encode a stream as N-Triples")
@In(StreamReceiver.class)
@Out(String.class)
public class PipeEncodeTriples extends AbstractGraphPipeEncoder {
	Model model;
	Stack<Resource> resources;
	private final AtomicInteger ATOMIC_INT = new AtomicInteger();
	// dummy subject to store data even if the subject is unknown at first
	final static String DUMMY_SUBJECT = "dummy_subject";
	final static String HTTP = "^[hH][tT][Tt][Pp][sS]?://.*";
	final static String BNODE = "^_:.*";
	final static String FTP = "^[Ff][Tt][Pp][sS]?://.*";

	final static String URN = "urn";
	final static String PROPERTY_AS_LITERALS = "Order";
	private boolean fixSubject = false;
	private static final Logger LOG =
			LogManager.getLogger(PipeEncodeTriples.class);
	private boolean storeUrnAsUri = false;
	private TreeMap<Integer, RDFList> treeMapOfRDFList;
	HashMap<String, TreeMap<Integer, RDFList>> hashMapOfTreeMapOfRDFList;
	private int rdfListNr;
	private boolean isRdfList;

	/**
	 * Sets the default temporary subject.
	 */
	public PipeEncodeTriples() {
		subject = DUMMY_SUBJECT;
	}

	/**
	 * Allows to define the subject from outside, e.g. from a flux file.
	 * 
	 * @param subject set the subject for each triple
	 */
	public void setSubject(final String subject) {
		this.subject = subject;
		fixSubject = true;
	}

	/**
	 * Allows to store URN's as URI's- Default is to store them as literals.
	 * 
	 * @param storeUrnAsUri set if urn's should be stored as URIs
	 */
	public void setStoreUrnAsUri(final String storeUrnAsUri) {
		this.storeUrnAsUri = Boolean.parseBoolean(storeUrnAsUri);
	}

	@Override
	public void startRecord(final String identifier) {
		model = ModelFactory.createDefaultModel();
		resources = new Stack<>();
		rdfListNr = -1;
		treeMapOfRDFList = new TreeMap<>();
		hashMapOfTreeMapOfRDFList = new HashMap<>();
		if (!fixSubject) {
			subject = DUMMY_SUBJECT;
		}
		resources.push(model.createResource(subject));
	}

	@Override
	public void literal(final String name, final String value) {
		if (value == null || value.isEmpty()) {
			LOG.debug("Value empty => ignoring triple: <" + subject + "> <" + name
					+ "> \"\". ");
			return;
		}
		if (name.equalsIgnoreCase(SUBJECT_NAME)) {
			subject = value;
			try {
				// insert subject now if it was not at the beginning of the record
				if (resources.peek().hasURI((DUMMY_SUBJECT)))
					ResourceUtils.renameResource(model.getResource(DUMMY_SUBJECT),
							subject);
				resources.push(model.createResource(subject));
			} catch (Exception e) {
				LOG.warn("Problem with name=" + name + " value=" + value, e);
			}
		} else if (name.matches(HTTP)) {
			try {
				final Property prop = model.createProperty(name);
				if (!name.contains(PROPERTY_AS_LITERALS)
						&& !name.endsWith("terms/title")
						&& (value.matches(HTTP) || value.matches(BNODE)
								|| value.matches(FTP)
								|| (value.startsWith(URN) && storeUrnAsUri)
								|| value.startsWith("mailto"))) {
					boolean uri = true;
					// either add uri ...
					if (!isRdfList)
						resources.peek().addProperty(prop,
								model.asRDFNode(NodeFactory.createURI(value)));
					else
						addRdfList(uri, name, value, prop);
				} else { // ... or add literal
					if (!isRdfList)
						resources.peek().addProperty(prop, value);
					else
						addRdfList(false, name, value, prop);
				}
			} catch (Exception e) {
				LOG.warn("Problem with name=" + name + " value=" + value, e);
			}
		}
	}

	private void addRdfList(final boolean isUri, final String name,
			final String value, final Property prop) {
		RDFNode node =
				isUri ? model.createProperty(value) : model.createLiteral(value);
		if (!hashMapOfTreeMapOfRDFList.containsKey(name)
				|| hashMapOfTreeMapOfRDFList.get(name).get(rdfListNr) == null) {
			treeMapOfRDFList = new TreeMap<>();
			treeMapOfRDFList.put(rdfListNr, model.createList(new RDFNode[] { node }));
			hashMapOfTreeMapOfRDFList.put(name, treeMapOfRDFList);
			addToResources(prop, name);
		} else if (hashMapOfTreeMapOfRDFList.get(name).get(rdfListNr) != null) {
			hashMapOfTreeMapOfRDFList.get(name).get(rdfListNr).add(node);
			addToResources(prop, name);
		}
	}

	private void addToResources(final Property prop, final String name) {
		resources.peek().addProperty(prop,
				hashMapOfTreeMapOfRDFList.get(name).get(rdfListNr));
	}

	Resource makeBnode(final String value) {
		final Resource res = model.createResource(
				new AnonId("_:" + value + ATOMIC_INT.getAndIncrement()));
		model.add(resources.peek(), model.createProperty(value), res);
		return res;
	}

	void enterBnode(final Resource res) {
		this.resources.push(res);
	}

	/**
	 * Treats bnodes and also rdf-lists .
	 * 
	 */
	@Override
	public void startEntity(String name) {
		if (name.startsWith(LIST_NAME)) {
			isRdfList = true;
			rdfListNr = name.matches(".*[0-9]{2}$")
					? Integer.valueOf(name.substring(name.length() - 2)) : 0;
		} else
			enterBnode(makeBnode(name));
	}

	@Override
	public void endEntity() {
		if (isRdfList) {
			isRdfList = false;
			rdfListNr = -1;
		} else
			this.resources.pop();
	}

	@Override
	public void endRecord() {
		final StringWriter tripleWriter = new StringWriter();
		RDFDataMgr.write(tripleWriter, model, Lang.NTRIPLES);
		getReceiver().process(tripleWriter.toString());
	}
}
