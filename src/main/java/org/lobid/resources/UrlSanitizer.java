/* Copyright 2016 hbz, Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources;

import org.apache.commons.validator.routines.UrlValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.morph.functions.AbstractSimpleStatelessFunction;

/**
 * Checks if a string is a valid absolute URL. If it is not, try to repair the
 * URL. Returns an empty string if it can't be repaired.
 * 
 * @author Pascal Christoph (dr0i)
 */
public final class UrlSanitizer extends AbstractSimpleStatelessFunction {
	private static final Logger LOG = LogManager.getLogger(UrlSanitizer.class);
	static UrlValidator urlValidator =
			new UrlValidator(UrlValidator.ALLOW_2_SLASHES);

	@Override
	public String process(final String value) {
		return sanitizeUrl(value);
	}

	private static String sanitizeUrl(final String value) {
		// unwise characters (rfc2396) :
		String url = value.replace("\\", "%5C").replace("|", "%7C");
		url = url.replaceAll("<.*>", "");
		url = url.trim();
		if (url.matches(".*#.*#.*")) {// allow only one fragment
			url = url.substring(0, (url.indexOf("#", url.indexOf("#") + 1)));
		}
		url = url.replace(" ", "%20");// space in URI
		if (!urlValidator.isValid(url)) {
			for (String urlSplitter : url.split("%20")) {
				if (urlValidator.isValid(urlSplitter))
					return urlSplitter;
			}
			if (url.matches(".*:/[^/].*")) // only one slash following the scheme
				url = url.replace(":/", "://");
			// assuming scheme is missing
			if (!urlValidator.isValid(url)) {
				url = "http://" + url;
				if (!urlValidator.isValid(url)) {
					url = "";
					LOG.info("No absolute URI could be generated from '" + value + "'");
				}
			}
		}
		return url;
	}
}
