/* Copyright 2018 Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources;

import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.Description;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

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
