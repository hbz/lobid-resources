/* Copyright 2018 Pascal Christoph, hbz. Licensed under the Eclipse Public License 2.0 */
package org.lobid.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.Description;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.impl.NQuadRDFParser;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;

/**
 * Converts an RDF graph to json-ld.
 * 
 * @author Pascal Christoph (dr0i)
 *
 */
@Description("Encodes an rdf graph (ntriples) into a JSON-LD map.")
@In(String.class)
@Out(Map.class)
public class RdfGraphToJsonLd
		extends DefaultObjectPipe<String, ObjectReceiver<Map<String, Object>>> {
	Object context;
	private String rdfTypeToIdentifyRootId =
			"http://purl.org/dc/terms/BibliographicResource";
	private static final Logger LOG =
			LogManager.getLogger(RdfGraphToJsonLd.class);
	private HashMap<String, String> frame;
	private String contextFn = "web/conf/context.jsonld";
	private String contextUri = "http://lobid.org/resources/context.jsonld";
	NQuadRDFParser rdfParser = new NQuadRDFParser();
	JsonLdOptions options = new JsonLdOptions();

	@Override
	public void onSetReceiver() {
		frame = new HashMap<>(ImmutableMap.of(//
				"@type", rdfTypeToIdentifyRootId, "@embed", "@always"));
		options.setCompactArrays(true);
		options.setPruneBlankNodeIdentifiers(true);
		options.setProcessingMode(JsonLdOptions.JSON_LD_1_1);
		try {
			String contextStr = new String(Files.readAllBytes(Paths.get(contextFn)));
			context = JsonUtils.fromString(contextStr);
		} catch (IOException e) {
			LOG.error("Couldn't load context at '" + contextFn
					+ "'. Please set context file location properly via constructor."
					+ e.getLocalizedMessage());
		}
	}

	@Override
	public void process(final String ntriples) {
		if (ntriples.isEmpty())
			return;
		try {
			Object jsonObject = JsonLdProcessor.fromRDF(ntriples, options, rdfParser);
			jsonObject = JsonLdProcessor.frame(jsonObject, frame, options);
			Map<String, Object> jsonMap =
					JsonLdProcessor.compact(jsonObject, context, options);
			jsonMap.put("@context", contextUri);
			getReceiver().process(jsonMap);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sets the context uri which substitutes the context in the json-ld documents
	 * so that these documents are not so bloated.
	 * 
	 * @param CONTEXT_URI the uri of the context which appears in documents
	 */
	public void setContextUri(final String CONTEXT_URI) {
		contextUri = CONTEXT_URI;
	}

	/**
	 * Sets the context's location filename. The context is used to convert a json
	 * document to json-ld.
	 * 
	 * @param FN the filename of the context which is used to produce the
	 *          documents
	 */
	public void setContextLocationFilname(final String FN) {
		contextFn = FN;
	}

	/**
	 * Gets the context uri which substitutes the context in the json-ld documents
	 * so that these documents are not so bloated.
	 * 
	 * @return the uri of the context which appears in documents
	 */
	public String getContextUri() {
		return this.contextUri;
	}

	/**
	 * Gets the context's location filename. The context is used to convert a json
	 * document to json-ld.
	 * 
	 * @return the filename of the context which is used to produce the documents
	 */
	public String getContextLocationFilename() {
		return this.contextFn;
	}

	/**
	 * Sets the rdf:type to search for to identiofy the main root id of the
	 * resource.
	 * 
	 * @param RDF_TYPE_TO_IDENTIFY_ROOT_ID the object of the rdf:type
	 */
	public void setRdfTypeToIdentifyRootId(
			final String RDF_TYPE_TO_IDENTIFY_ROOT_ID) {
		this.rdfTypeToIdentifyRootId = RDF_TYPE_TO_IDENTIFY_ROOT_ID;
	}
}
