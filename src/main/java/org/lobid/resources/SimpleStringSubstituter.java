/* Copyright 2018 Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources;

import org.metafacture.framework.ObjectReceiver;
import org.metafacture.framework.annotations.Description;
import org.metafacture.framework.annotations.In;
import org.metafacture.framework.annotations.Out;
import org.metafacture.framework.helpers.DefaultObjectPipe;

/**
 * Removes the first match of the given pattern from the input string.
 * 
 * @author Pascal Christoph
 */
@Description("A simple filter. Removes the first match of the given pattern from the input string.")
@In(String.class)
@Out(String.class)
public class SimpleStringSubstituter
		extends DefaultObjectPipe<String, ObjectReceiver<String>> {
	private static String toBeSubstitutedRegex;
	private static String substitute;

	SimpleStringSubstituter(final String toBeSubstituted,
			final String substitute) {
		SimpleStringSubstituter.toBeSubstitutedRegex = toBeSubstituted;
		SimpleStringSubstituter.substitute = substitute;
	}

	@Override
	public void process(final String str) {
		getReceiver().process(str.replaceFirst(toBeSubstitutedRegex, substitute));
	}
}
