/* Copyright 2015-2018 hbz. Licensed under the EPL 2.0 */

import java.io.File;

import org.junit.Test;

import de.hbz.lobid.helper.EtikettMaker;

/**
 *
 * @author Pascal Christoph (dr0i)
 *
 */

@SuppressWarnings("javadoc")
public class TestGenerateContext {

	@Test
	public void writeContext() {
		// resources
		EtikettMaker em = new EtikettMaker(new File(Thread.currentThread()
				.getContextClassLoader().getResource("labels").getFile()));
		em.setContextLocation("web/conf/context.jsonld");
		em.writeContext();
	}
}
