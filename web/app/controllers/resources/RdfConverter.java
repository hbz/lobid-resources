/* Copyright 2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.resources;

import java.io.IOException;
import java.io.StringWriter;

import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaTripleCallback;
import com.github.jsonldjava.utils.JsonUtils;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.shared.PrefixMapping;

import play.Logger;

/**
 * Helper class for converting JsonLd to RDF.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class RdfConverter {
	/**
	 * RDF serialization formats.
	 */
	@SuppressWarnings("javadoc")
	public static enum RdfFormat {
		RDF_XML("RDF/XML"), //
		N_TRIPLE("N-TRIPLE"), //
		TURTLE("TURTLE");

		private final String name;

		RdfFormat(final String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	/**
	 * @param jsonLd The JSON-LD string to convert
	 * @param format The RDF format to serialize the jsonLd to
	 * @return The input, converted to the given RDF serialization, or null
	 */
	public static String toRdf(final String jsonLd, final RdfFormat format) {
		try {
			final Object jsonObject = JsonUtils.fromString(jsonLd);
			final JenaTripleCallback callback = new JenaTripleCallback();
			final Model model = (Model) JsonLdProcessor.toRDF(jsonObject, callback);
			model.setNsPrefixes(PrefixMapping.Extended);
			final StringWriter writer = new StringWriter();
			model.write(writer, format.getName());
			return writer.toString();
		} catch (IOException | JsonLdError e) {
			Logger.error(e.getMessage(), e);
		}
		return null;
	}
}
