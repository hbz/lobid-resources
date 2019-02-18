package org.lobid.resources;

import org.culturegraph.mf.framework.DefaultTee;
import org.culturegraph.mf.framework.ObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.Tee;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;
import org.culturegraph.mf.stream.pipe.ObjectPipeDecoupler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Divides incoming objects and distributes them to added receivers. These
 * receivers are coupled with an
 * {@link org.culturegraph.mf.stream.pipe.ObjectPipeDecoupler}, so each added
 * receiver runs in its own thread.
 * 
 * @param <T> Object type
 *
 * @author Pascal Christoph(dr0i)
 * 
 */
@In(Object.class)
@Out(Object.class)
public class ObjectThreader<T> extends DefaultTee<ObjectReceiver<T>>
		implements ObjectPipe<T, ObjectReceiver<T>> {

	private static final Logger LOG =
			LoggerFactory.getLogger(ObjectThreader.class);
	private int objectNumber = 0;

	@Override
	public void process(final T obj) {
		getReceivers().get(objectNumber).process(obj);
		if (objectNumber == getReceivers().size() - 1)
			objectNumber = 0;
		else
			objectNumber++;
	}

	@Override
	public <R extends ObjectReceiver<T>> R setReceiver(final R receiver) {
		return super.setReceiver(
				new ObjectPipeDecoupler<T>().setReceiver(receiver));
	}

	@Override
	public Tee<ObjectReceiver<T>> addReceiver(final ObjectReceiver<T> receiver) {
		LOG.info("Adding thread " + getReceivers().size() + 1);
		ObjectPipeDecoupler<T> opd = new ObjectPipeDecoupler<>();
		opd.setReceiver(receiver);
		return super.addReceiver(opd);
	}
}
