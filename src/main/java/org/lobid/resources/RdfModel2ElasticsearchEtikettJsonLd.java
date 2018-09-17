/* Copyright 2013-2015 Fabian Steeg, Pascal Christoph, hbz. Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.jena.riot.RDFDataMgr;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import de.hbz.lobid.helper.EtikettMaker;
import de.hbz.lobid.helper.EtikettMakerInterface;
import de.hbz.lobid.helper.JsonConverter;

/**
 * Converts a jena model to JSON-LD document(s) consumable by elasticsearch.
 * Only item nodes in the graph will become a document on their own, redundantly
 * kept part of the main node.
 * 
 * @author Fabian Steeg (fsteeg)
 * @author Pascal Christoph (dr0i)
 */
@In(Model.class)
@Out(HashMap.class)
public final class RdfModel2ElasticsearchEtikettJsonLd
		extends DefaultObjectPipe<Model, ObjectReceiver<HashMap<String, String>>> {
	private static final Logger LOG =
			LogManager.getLogger(RdfModel2ElasticsearchEtikettJsonLd.class);
	// the items will have their own index type and ES parents
	private static final String PROPERTY_TO_PARENT = "itemOf";
	static String LOBID_DOMAIN = "http://lobid.org/";
	// the sub node we want to create besides the main node
	private static String LOBID_ITEM_URI_PREFIX = LOBID_DOMAIN + "items/";
	private static String mainNodeId = null;
	private static final String TYPE_ITEM = "item";
	private static final String TYPE_RESOURCE = "resource";
	private Object jsonLdContext;
	private Pattern internalIdPattern =
			Pattern.compile("^" + LOBID_DOMAIN + ".*");
	private String labelsDirectoryName = "labels";
	private EtikettMakerInterface etikettMaker;
	private String predicateAssociatedWithTheRootSubject =
			"http://purl.org/lobid/lv#hbzID";
	private static final String PREDICATE_TO_IDENTIFY_ROOT_SUBJECT_OF_ITEMS =
			"http://www.w3.org/2007/05/powder-s#describedby";
	JsonConverter jc;

	/**
	 * Provides default constructor. Every json ld document gets the whole json ld
	 * context defined in in the default @see{labelsDirectoryName} which is then
	 * constructed via @see{EtikettMaker};
	 */
	public RdfModel2ElasticsearchEtikettJsonLd() {
		this("default");
	}

	/**
	 * Provides a json ld context. May be a json string or a http URI as string.
	 * If its an URI the URI will be the value of the @context-field. If it's a
	 * whole json string, the whole string is added under the @context field:
	 * 
	 * @param jsonLdContext May be a json as string or a http uri as string.
	 */
	public RdfModel2ElasticsearchEtikettJsonLd(final Object jsonLdContext) {
		setup(jsonLdContext);
	}

	/**
	 * Takes a filename which could be a directory to create the context jsonld
	 * out of labels. Second parameter is the value of the jsonld @context field.
	 * 
	 * @param fn the name of the file
	 * @param jsonContextLd the content of the @context field of the jsonld
	 */
	public RdfModel2ElasticsearchEtikettJsonLd(final File fn,
			final String jsonContextLd) {
		labelsDirectoryName = fn.getPath();
		LOG.info("use labels directory: " + labelsDirectoryName);
		setup(jsonContextLd);
	}

	/**
	 * Sets the pattern for identifiying the root node.
	 *
	 * @param pat the pattern identifying the root node.
	 */
	public void setIdPatternMainNode(Pattern pat) {
		internalIdPattern = pat;
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
		jc = new JsonConverter(etikettMaker);
	}

	@Override
	public void process(final Model originModel) {
		mainNodeId = null;
		extractItemFromResourceModel(originModel);
	}

	private void extractItemFromResourceModel(final Model originalModel) {
		Model copyOfOriginalModel =
				ModelFactory.createModelForGraph(originalModel.getGraph());
		final ResIterator subjectsIterator = originalModel.listSubjects();
		// iterate through all nodes
		while (subjectsIterator.hasNext()) {
			final Resource subjectResource = subjectsIterator.next();
			Model submodel = ModelFactory.createDefaultModel();
			// extract sub nodes (items) => will become documents on their own
			if (subjectResource.isURIResource()) {
				if (subjectResource.getURI().startsWith(LOBID_ITEM_URI_PREFIX)) {
					submodel = extractSubmodel(submodel, subjectResource);
					toJson(submodel,
							subjectResource.getURI().toString().replaceAll("#!$", ""));
					// remove some properties from that item which clings to the main node
					removeProperty(copyOfOriginalModel, subjectResource, "itemOf");
					removeProperty(copyOfOriginalModel, subjectResource, "describedby");
				} else if (mainNodeId == null && internalIdPattern
						.matcher(subjectResource.getURI().toString()).matches())
					setMainNodeId(subjectResource);
			}
		} // the main node with all the sub nodes (items)
		toJson(copyOfOriginalModel, mainNodeId);
	}

	private static void removeProperty(Model model, Resource subjectResource,
			String propertyName) {
		StmtIterator stmtIt = subjectResource.listProperties();
		while (stmtIt.hasNext()) {
			Statement stmt = stmtIt.nextStatement();
			if (stmt.getPredicate().getLocalName().equals(propertyName)) {
				model.remove(stmt);
				break;
			}
		}
	}

	private static Model extractSubmodel(Model submodel,
			Resource subjectResource) {
		StmtIterator stmtIt = subjectResource.listProperties();
		while (stmtIt.hasNext()) {
			Statement stmt = stmtIt.nextStatement();
			submodel.add(stmt);
		}
		return submodel;
	}

	private void setMainNodeId(Resource subjectResource) {
		StmtIterator stmtIt = subjectResource.listProperties();
		while (stmtIt.hasNext()) {
			Statement stmt = stmtIt.nextStatement();
			if (stmt.getPredicate().toString()
					.equals(predicateAssociatedWithTheRootSubject)) {
				mainNodeId = subjectResource.getURI().toString().replaceAll("#!$", "");
				break;
			}
		}
		return;
	}

	/**
	 * Creates and pushes two documents: the json document with the index
	 * properties and the json document itself. The 'expanded' JSON-LD
	 * serialization is used to guarantee consistent field types.
	 * 
	 * @param model
	 * @param id
	 */
	private void toJson(Model model, String id) {
		if (model.isEmpty())
			return;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			jc = new JsonConverter(etikettMaker);
			if (id.startsWith(LOBID_ITEM_URI_PREFIX)) {
				jc.setRootIdPredicate(PREDICATE_TO_IDENTIFY_ROOT_SUBJECT_OF_ITEMS);
			} else
				jc.setRootIdPredicate(predicateAssociatedWithTheRootSubject);
			RDFDataMgr.write(out, model, org.apache.jena.riot.RDFFormat.NTRIPLES);
			Map<String, Object> jsonMap =
					jc.convertLobidData(new ByteArrayInputStream(out.toByteArray()),
							org.openrdf.rio.RDFFormat.NTRIPLES, id, jsonLdContext);
			getReceiver().process(addInternalProperties(new HashMap<String, String>(),
					id, JsonConverter.getObjectMapper().writeValueAsString(jsonMap)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static HashMap<String, String> addInternalProperties(
			HashMap<String, String> jsonMap, String id, String json) {
		String type = TYPE_RESOURCE;
		String idWithoutDomain = id.replaceAll(LOBID_DOMAIN + ".*/", "");

		if (id.startsWith(LOBID_ITEM_URI_PREFIX)) {
			type = TYPE_ITEM;
			try {
				JsonNode node = new ObjectMapper().readValue(json, JsonNode.class);
				final JsonNode parent = node.path(PROPERTY_TO_PARENT);
				String p = parent != null
						? parent.findValue("id").asText()
								.replaceAll(LOBID_DOMAIN + ".*/", "").replaceFirst("#!$", "")
						: null;
				if (p == null) {
					LOG.warn("Item " + idWithoutDomain + " has no parent declared!");
					jsonMap.put(ElasticsearchIndexer.Properties.PARENT.getName(),
							"no_parent");
				} else
					jsonMap.put(ElasticsearchIndexer.Properties.PARENT.getName(), p);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		jsonMap.put(ElasticsearchIndexer.Properties.GRAPH.getName(), json);
		jsonMap.put(ElasticsearchIndexer.Properties.TYPE.getName(), type);
		jsonMap.put(ElasticsearchIndexer.Properties.ID.getName(), idWithoutDomain);
		return jsonMap;
	}

	/**
	 * Identifies the main node Id aka the root subject. This is important.
	 * 
	 * @param rootId identifies the root subject by this property. Default is
	 *          specified in {@link #getRootIdPredicate()}
	 */
	public void setRootIdPredicate(final String rootId) {
		this.predicateAssociatedWithTheRootSubject = rootId;
		jc.setRootIdPredicate(rootId);
	}

	/**
	 * Gets the property which identifies the main node Id aka the root subject.
	 * This is important. The default is "http://purl.org/lobid/lv#hbzID".
	 * 
	 * @return the property which identifies the root subject as String
	 * 
	 */
	public String getRootIdPredicate() {
		return this.predicateAssociatedWithTheRootSubject;
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

}
