/* Copyright 2013 Fabian Steeg, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import org.culturegraph.mf.framework.DefaultStreamPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.StreamReceiver;
import org.culturegraph.mf.framework.annotations.Description;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

/**
 * @author Fabian Steeg, Pascal Christoph
 */
@Description("Superclass for graph-based pipe encoders")
@In(StreamReceiver.class)
@Out(String.class)
public abstract class AbstractGraphPipeEncoder
		extends DefaultStreamPipe<ObjectReceiver<String>> {

	static final String SUBJECT_NAME = "~rdf:subject";
	static final String LIST_NAME = "~rdf:list";
	String subject;

}
