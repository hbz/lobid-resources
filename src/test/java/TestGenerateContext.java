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
	}
}
