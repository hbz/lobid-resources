/* Copyright 2013 Fabian Steeg, Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources;

import org.metafacture.framework.ObjectReceiver;
import org.metafacture.framework.StreamReceiver;
import org.metafacture.framework.annotations.Description;
import org.metafacture.framework.annotations.In;
import org.metafacture.framework.annotations.Out;
import org.metafacture.framework.helpers.DefaultStreamPipe;

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
