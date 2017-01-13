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
		new EtikettMaker(Thread.currentThread().getContextClassLoader()
				.getResourceAsStream("labels.json")).writeContext();
	}
}
