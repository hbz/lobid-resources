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

import java.net.URI;
import java.net.URISyntaxException;

import org.culturegraph.mf.morph.functions.AbstractSimpleStatelessFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks if an string is a valid absolute URI. If it is not, try to repair the
 * URI. At last enforce a (dummy) one.
 * 
 * @author Pascal Christoph (dr0i)
 */
public final class EnforceUri extends AbstractSimpleStatelessFunction {
	private static final Logger LOG = LoggerFactory.getLogger(EnforceUri.class);

	@Override
	public String process(final String value) {
		return enforceUri(value);
	}

	private static String enforceUri(final String value) {
		String enforcedUri = value;
		if (!isUri(enforcedUri)) {
			enforcedUri = enforcedUri.replace(" ", "%20");// space in URI
			if (!isUri(enforcedUri)) {
				if (!enforcedUri.matches(".{3}[:.]{3}")) // no scheme ("ftp", "https")
					enforcedUri = "http://" + enforcedUri;
			}
			if (!isUri(enforcedUri)) {
				enforcedUri = enforcedUri.split("%20")[0];
			}
			if (!isUri(enforcedUri)) {
				enforcedUri = "http://dummyUriBecauseOfWrongCatalogization.22";
				LOG.info("No absolute URI could be generated from '" + value
						+ "', using dummy URI");
			}
		}
		return enforcedUri;
	}

	private static boolean isUri(final String value) {
		boolean isUri = true;
		try {
			URI uri = new URI(value);
			if (!uri.isAbsolute())
				isUri = false;
		} catch (URISyntaxException e) {
			isUri = false;
		}
		if (!isUri)
			LOG.debug("No (absolute) URI: '" + value + "'");
		return isUri;
	}
}
