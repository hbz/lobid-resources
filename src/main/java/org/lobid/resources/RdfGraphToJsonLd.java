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
	static private String contextFn;
	private static final Logger LOG =
			LogManager.getLogger(RdfGraphToJsonLd.class);
	private final HashMap<String, String> frame = new HashMap<>(ImmutableMap.of(//
			"@type", "http://purl.org/dc/terms/BibliographicResource", "@embed",
			"@always"));

	/**
	 * @param contextFn the filename of the context
	 */
	public RdfGraphToJsonLd(String contextFn) {
		RdfGraphToJsonLd.contextFn = contextFn;
	}

	@Override
	public void onSetReceiver() {
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
			NQuadRDFParser rdfParser = new NQuadRDFParser();
			JsonLdOptions options = new JsonLdOptions();
			options.setCompactArrays(true);
			options.setPruneBlankNodeIdentifiers(true);
			Object jsonObject = JsonLdProcessor.fromRDF(ntriples, options, rdfParser);
			jsonObject = JsonLdProcessor.frame(jsonObject, frame, options);
			Map<String, Object> jsonMap =
					JsonLdProcessor.compact(jsonObject, context, options);
			jsonMap.put("@context", "http://lobid.org/resources/context.jsonld");
			getReceiver().process(jsonMap);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
