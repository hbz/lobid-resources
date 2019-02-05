package org.lobid.resources;

import java.util.concurrent.atomic.AtomicInteger;

import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.Description;
import org.culturegraph.mf.framework.annotations.FluxCommand;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;
import org.culturegraph.mf.morph.functions.AbstractSimpleStatelessFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Divides incoming objects. Ignore every object but of the n-th, which is
 * passed to the receiver. Combine it with a
 * {@link org.culturegraph.mf.stream.pipe.ObjectPipeDecoupler} and add multiple
 * of these using a tee like e.g.
 * {@link org.culturegraph.mf.stream.pipe.ObjectTee} to get multithreading.
 * 
 * @param <T> Object type
 *
 * @author Pascal Christoph(dr0i)
 * 
 */
@In(Object.class)
@Out(Object.class)
@Description("Reads a directory and emits all filenames found.")
@FluxCommand("read-dir")
public final class ObjectDivider<T> extends DefaultObjectPipe<T, ObjectReceiver<T>> {
	private static final Logger LOG =
			LoggerFactory.getLogger(AbstractSimpleStatelessFunction.class);
	static AtomicInteger instances = new AtomicInteger();
	private int instanceNumber;
	private int objectNumber = 1;

	/**
	 * Every Divider gets a unique id.
	 * 
	 */
	public ObjectDivider() {
		instanceNumber = instances.incrementAndGet();
		LOG.info("Created new Divider instance number: " + instanceNumber);
	}

	@Override
	public void process(final T obj) {
		if (objectNumber == instanceNumber)
			getReceiver().process(obj);
		if (objectNumber == instances.get())
			objectNumber = 1;
		else
			objectNumber++;
	}
}
