package tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * All quick tests, including integration tests with dependencies on external
 * services. For quick, self-contained tests see {@link TravisTests}.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
@RunWith(Suite.class)
@SuiteClasses({ ApplicationTest.class, InternalIntegrationTest.class,
		ExternalIntegrationTest.class, AcceptUnitTest.class,
		AcceptIntegrationTest.class, IndexIntegrationTest.class,
		AggregationsTest.class })
public class AllQuickTests {
	//
}