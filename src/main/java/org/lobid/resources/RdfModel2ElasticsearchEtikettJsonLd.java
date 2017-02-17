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
import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Every node in the graph will be a document on its own, except when this is
 * declared otherwise (-> keep node).
 * 
 * @author Fabian Steeg (fsteeg)
 * @author Pascal Christoph (dr0i)
 */
@In(Model.class)
@Out(HashMap.class)
public final class RdfModel2ElasticsearchEtikettJsonLd
		extends DefaultObjectPipe<Model, ObjectReceiver<HashMap<String, String>>> {
	private static final Logger LOG =
			LoggerFactory.getLogger(RdfModel2ElasticsearchEtikettJsonLd.class);
	// the items will have their own index type and ES parents
	private static final String PROPERTY_TO_PARENT = "itemOf";
	static String LOBID_DOMAIN = "http://lobid.org/";
	private static String LOBID_ITEM_URI_PREFIX = LOBID_DOMAIN + "items/";
	// the sub node we want to cling to the main node
	private static final String KEEP_NODE_PREFIX = "http://d-nb.info/gnd";
	private static final String KEEP_NODE_MAIN_PREFIX =
			LOBID_DOMAIN + "resources/";
	private static String mainNodeId = null;
	private static final String TYPE_ITEM = "item";
	private static final String TYPE_RESOURCE = "resource";
	private static Object JSONLD_CONTEXT;
	private final Pattern INTERNAL_ID_PATTERN =
			Pattern.compile("^" + LOBID_DOMAIN + ".*");
	private static EtikettMakerInterface etikettMaker =
			new EtikettMaker(new File(Thread.currentThread().getContextClassLoader()
					.getResource("labels").getFile()));

	/**
	 * Provides default constructor. Every json ld document gets the whole json ld
	 * context defined in @see{EtikettMaker};
	 */
	public RdfModel2ElasticsearchEtikettJsonLd() {
		this(etikettMaker.getContext().get("@context"));
	}

	/**
	 * Provides a json ld context. May be a json string or a http URI as string.
	 * If its an URI the URI will we the value of the @context-field. If it's a
	 * whole json string, the whole string is added under the @context field:
	 * 
	 * @param jsonLdContext May be a json as string or a http uri as string.
	 */
	public RdfModel2ElasticsearchEtikettJsonLd(final Object jsonLdContext) {
		RdfModel2ElasticsearchEtikettJsonLd.JSONLD_CONTEXT = jsonLdContext;
		if (jsonLdContext.toString().substring(0, 4).equalsIgnoreCase("http"))
			LOG.info("Using context URI: " + jsonLdContext);
		else
			LOG.info("Adding json ld context to every document");
	}

	@Override
	public void process(final Model originModel) {
		mainNodeId = null;
		splitModel2ItemAndResourceModel(originModel);
	}

	private void splitModel2ItemAndResourceModel(final Model originalModel) {
		Model copyOfOriginalModel =
				ModelFactory.createModelForGraph(originalModel.getGraph());
		final ResIterator subjectsIterator = originalModel.listSubjects();
		// iterate through all nodes
		while (subjectsIterator.hasNext()) {
			final Resource subjectResource = subjectsIterator.next();
			Model submodel = ModelFactory.createDefaultModel();
			if (!subjectResource.isAnon()) {
				// only extract sub nodes we don't want to keep in the main model
				if (!subjectResource.getURI().startsWith(KEEP_NODE_PREFIX)
						&& !subjectResource.getURI().startsWith(KEEP_NODE_MAIN_PREFIX)) {
					if (shouldSubmodelBeExtracted(submodel, subjectResource)) {
						toJson(submodel,
								subjectResource.getURI().toString().replaceAll("#!$", ""));
					}
				} else if (mainNodeId == null && INTERNAL_ID_PATTERN
						.matcher(subjectResource.getURI().toString()).matches())
					mainNodeId =
							subjectResource.getURI().toString().replaceAll("#!$", "");
			}
			if (!submodel.isEmpty()) {
				// remove the newly created sub model from the main node
				copyOfOriginalModel.remove(submodel);
			}
		} // the main node (with its kept sub node)
		toJson(copyOfOriginalModel, mainNodeId);
	}

	// A sub model mustn't be extracted if the resource is to be kept as a sub
	// node of the main node. An bnode mustn't be extracted either.
	private static boolean shouldSubmodelBeExtracted(Model submodel,
			Resource subjectResource) {
		StmtIterator stmtIt = subjectResource.listProperties();
		while (stmtIt.hasNext()) {
			Statement stmt = stmtIt.nextStatement();
			// identifying the main node
			if (stmt.getObject().toString().startsWith(KEEP_NODE_PREFIX))
				return false;
			submodel.add(stmt);
		}
		return true;
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
			JsonConverter jc = new JsonConverter(etikettMaker);
			RDFDataMgr.write(out, model, org.apache.jena.riot.RDFFormat.NTRIPLES);
			Map<String, Object> jsonMap =
					jc.convertLobidData(new ByteArrayInputStream(out.toByteArray()),
							org.openrdf.rio.RDFFormat.NTRIPLES, "http://lobid.org",
							RdfModel2ElasticsearchEtikettJsonLd.JSONLD_CONTEXT);
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

}
