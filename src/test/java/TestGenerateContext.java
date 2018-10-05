/* Copyright 2015-2018 hbz. Licensed under the EPL 2.0 */

import java.io.File;

import org.junit.Test;

import de.hbz.lobid.helper.EtikettMaker;

/**
 * 
 * @author Jan Schnasse
 *
 */

@SuppressWarnings("javadoc")
public class TestGenerateContext {

	@Test
	public void writeContext() {
		new EtikettMaker(new File(Thread.currentThread().getContextClassLoader()
				.getResource("labels").getFile())).writeContext();
		// deletions index
		EtikettMaker em = new EtikettMaker(new File(Thread.currentThread()
				.getContextClassLoader().getResource("deletion-labels").getFile()));
		em.setContextLocation("web/conf/context-deletion.jsonld");
		em.writeContext();
		// loc bibframe
		em = new EtikettMaker(new File(Thread.currentThread()
				.getContextClassLoader().getResource("loc-bibframe-labels").getFile()));
		em.setContextLocation("web/conf/context-loc.jsonld");
		em.writeContext();
	}
}
