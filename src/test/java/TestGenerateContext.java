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
		// deletions index
		em = new EtikettMaker(new File(Thread.currentThread()
				.getContextClassLoader().getResource("deletion-labels").getFile()));
		em.setContextLocation("web/conf/context-deletion.jsonld");
		em.writeContext();
	}
}
