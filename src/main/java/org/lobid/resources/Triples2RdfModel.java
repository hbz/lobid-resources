/* Copyright 2013 Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.stream.Collectors;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.RDFSRuleReasonerFactory;
import org.apache.jena.riot.RiotException;
import org.apache.jena.util.FileManager;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.ReasonerVocabulary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.metafacture.framework.ObjectReceiver;
import org.metafacture.framework.annotations.Description;
import org.metafacture.framework.annotations.In;
import org.metafacture.framework.annotations.Out;
import org.metafacture.framework.helpers.DefaultObjectPipe;


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
        }
        else {
            Model model = ModelFactory.createDefaultModel();
            try {
                String seb = new BufferedReader(new StringReader(str)).lines()
                    .filter((line) -> validTriple(line, model))
                    .collect(Collectors.joining("\n"));
                model.read(new StringReader(seb), "test:uri" + count++, serialization);
                if (inferencing) {
                    reasoning(model);
                }
                getReceiver().process(model);
            }
            catch (Exception e) {
                LOG.error("Exception in " + str, e);
            }
        }
	}

	private boolean validTriple(String triple, Model model) {
		try {
			model.read(new StringReader(triple), "test:uri" + count++, serialization);
		} catch (Exception e) {
			LOG.info("... ignore broken triple");
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
			Triple triple = it.next();
			if (!triple.getObject().isBlank()) {
				model1.add(model.asStatement(triple));
			}
		}
		model.add(model1);
	}
}
