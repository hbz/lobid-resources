/* Copyright 2013 Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.stream.Collectors;

import org.apache.jena.riot.RiotException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.Description;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.reasoner.Reasoner;
import com.hp.hpl.jena.reasoner.rulesys.RDFSRuleReasonerFactory;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import com.hp.hpl.jena.vocabulary.ReasonerVocabulary;

/**
 * Encodes triples into a jena RDF Model. Usage of inferencing by applying an
 * ontology is possible.
 * 
 * @author Pascal Christoph
 */
@Description("Encodes triples into an RDF Model. Usage of inferencing by applying "
		+ "an ontology is possible. Predefined values for input are"
		+ " 'RDF/XML', 'N-TRIPLE', 'TURTLE' (or 'TTL') and 'N3'. null represents the "
		+ "default language, 'RDF/XML'. 'RDF/XML-ABBREV' is a synonym for 'RDF/XML'."
		+ "Default is 'TURTLE'.")
@In(String.class)
@Out(Model.class)
public class Triples2RdfModel
		extends DefaultObjectPipe<String, ObjectReceiver<Model>> {
	private int count = 0;
	private String serialization = "TURTLE";
	private static final Logger LOG =
			LogManager.getLogger(Triples2RdfModel.class);
	Reasoner boundReasoner;
	Property propertyIdentifyingTheNodeForInferencing;
	boolean inferencing;

	/**
	 * Sets the serialization format of the incoming triples .
	 * 
	 * @param serialization one of 'RDF/XML', 'N-TRIPLE', 'TURTLE' (or 'TTL') and
	 *          'N3'. null represents the default language, 'RDF/XML'.
	 *          'RDF/XML-ABBREV' is a synonym for 'RDF/XML'.")
	 */
	public void setInput(final String serialization) {
		this.serialization = serialization;
	}

	/**
	 * Sets the filename of the ontology. This ontology will be loaded into an
	 * inference model to gain statements about resources part of that ontology.
	 * The reasoner being used is the most simple one and considers only RDFS.
	 * 
	 * @param ontologyFilename the filename of the ontology
	 */
	public void setInferenceModel(String ontologyFilename) {
		Reasoner reasoner = RDFSRuleReasonerFactory.theInstance().create(null);
		reasoner.setParameter(ReasonerVocabulary.PROPsetRDFSLevel,
				ReasonerVocabulary.RDFS_SIMPLE);
		boundReasoner =
				reasoner.bindSchema(FileManager.get().loadModel(ontologyFilename));
		this.inferencing = true;
	}

	/**
	 * Sets the property to identify the node for inferencing. As we want only
	 * statements about the "main" resource and discard all other resources which
	 * may be added by inferencing, this resource needs to be set.
	 * 
	 * @param property the property
	 */
	public void setPropertyIdentifyingTheNodeForInferencing(String property) {
		this.propertyIdentifyingTheNodeForInferencing =
				ResourceFactory.createProperty(property);
	}

	@Override
	public void process(final String str) {
		if (str.startsWith("<" + PipeEncodeTriples.DUMMY_SUBJECT)) {
			LOG.warn("Model without subject - skipping. Model size=" + str.length());
		} else {
			Model model = ModelFactory.createDefaultModel();
			try {
				model.read(new StringReader(str), "test:uri" + count++, serialization);
				if (inferencing)
					reasoning(model);
				getReceiver().process(model);
			} catch (RiotException rioe) {
				LOG.warn("Resource with some broken triples:\n" + str);
				String seb = new BufferedReader(new StringReader(str)).lines()
						.filter((line) -> validTriple(line, model))
						.collect(Collectors.joining("\n"));
				model.read(new StringReader(seb), "test:uri" + count++, serialization);
				getReceiver().process(model);
			} catch (Exception e) {
				LOG.error("Exception in " + str, e);
			}
		}
	}

	private boolean validTriple(String triple, Model model) {
		try {
			model.read(new StringReader(triple), "test:uri" + count++, serialization);
		} catch (Exception e) {
			LOG.info("... ignore broken triple: " + triple);
			return false;
		}
		return true;
	}

	private void reasoning(Model model) {
		ExtendedIterator<Triple> it = ModelFactory
				.createInfModel(boundReasoner, model).getGraph()
				.find(model
						.listSubjectsWithProperty(propertyIdentifyingTheNodeForInferencing)
						.next().asNode(), null, null);
		Model model1 = ModelFactory.createDefaultModel();
		while (it.hasNext()) {
			Triple triple = it.next().asTriple();
			if (!triple.getObject().isBlank()) {
				model1.add(model.asStatement(triple));
			}
		}
		model.add(model1);
	}
}
