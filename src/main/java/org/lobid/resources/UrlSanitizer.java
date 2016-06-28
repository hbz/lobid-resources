/*
 *  Copyright 2016 hbz, Pascal Christoph
 *
 *  Licensed under the Apache License, Version 2.0 the "License";
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.lobid.resources;

import org.apache.commons.validator.routines.UrlValidator;
import org.culturegraph.mf.morph.functions.AbstractSimpleStatelessFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks if a string is a valid absolute URL. If it is not, try to repair the
 * URL. Returns an empty string if it can't be repaired.
 * 
 * @author Pascal Christoph (dr0i)
 */
public final class UrlSanitizer extends AbstractSimpleStatelessFunction {
	private static final Logger LOG = LoggerFactory.getLogger(UrlSanitizer.class);
	static UrlValidator urlValidator =
			new UrlValidator(UrlValidator.ALLOW_2_SLASHES);

	@Override
	public String process(final String value) {
		return sanitizeUrl(value);
	}

	private static String sanitizeUrl(final String value) {
		String url = value.trim();
		if (!urlValidator.isValid(url)) {
			url = url.replace(" ", "%20");// space in URI
			if (!urlValidator.isValid(url)) {
				for (String urlSplitter : url.split("%20")) {
					if (urlValidator.isValid(urlSplitter))
						return urlSplitter;
				}
				if (!urlValidator.isValid(url)) {
					if (url.matches(".*:/[^/].*")) // only one slash following the scheme
						url = url.replace(":/", "://");
					// assuming scheme is missing
					if (!urlValidator.isValid(url)) {
						url = "http://" + url;
						if (!urlValidator.isValid(url)) {
							url = "";
							LOG.info(
									"No absolute URI could be generated from '" + value + "'");
						}
					}
				}
			}
		}
		return url;
	}
}
