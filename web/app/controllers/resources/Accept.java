/* Copyright 2014-2017 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import java.util.Collection;

import play.api.http.MediaRange;

/**
 * Helper class for dealing with content negotiation for the `Accept` header.
 *
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Accept {

	private Accept() {
		// static helper class, don't instantiate
	}

	enum Format {
		JSON_LD("json(.+)?", "application/json", "application/ld+json"), //
		BULK("jsonl", "application/x-jsonlines"), //
		HTML("html", "text/html"), //
		RDF_XML("rdf", "application/rdf+xml", "application/xml", "text/xml"), //
		N_TRIPLE("nt", "application/n-triples", "text/plain"), //
		TURTLE("ttl", "text/turtle", "application/x-turtle"), //
		RSS("rss", "application/rss+xml"), //
		MARC_XML("mrcx", "application/marcxml+xml");

		String[] types;
		String queryParamString;

		private Format(String format, String... types) {
			this.queryParamString = format;
			this.types = types;
		}

		public static Format of(String format) {
			for (Format f : Format.values()) {
				if (format.equals(f.queryParamString)) {
					return f;
				}
			}
			return Format.JSON_LD;
		}
	}

	/**
	 * @param formatParam The requested format parameter
	 * @param acceptedTypes The accepted types
	 * @return The selected format for the given parameter and types
	 */
	public static String formatFor(String formatParam,
			Collection<MediaRange> acceptedTypes) {
		for (Format format : Format.values())
			if (formatParam != null && formatParam.matches(format.queryParamString))
				return formatParam;
		for (MediaRange mediaRange : acceptedTypes)
			for (Format format : Format.values())
				for (String mimeType : format.types)
					if (mediaRange.accepts(mimeType))
						return format.queryParamString;
		return Format.JSON_LD.queryParamString;
	}

}
