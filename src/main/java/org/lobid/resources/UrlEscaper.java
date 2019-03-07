/* Copyright 2014 hbz, Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources;

import org.metafacture.metamorph.api.helpers.AbstractSimpleStatelessFunction;

import com.google.gdata.util.common.base.PercentEscaper;

/**
 * @author Pascal Christoph (dr0i)
 */
public final class UrlEscaper extends AbstractSimpleStatelessFunction {
	PercentEscaper percentEscaper =
			new PercentEscaper(PercentEscaper.SAFEPATHCHARS_URLENCODER, false);

	@Override
	public String process(final String value) {
		return percentEscaper.escape(value);
	}
}
