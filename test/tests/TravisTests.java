package tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * All quick, self-contained tests for running in Travis CI. For running all
 * tests, including long running tests and integration tests with dependencies
 * on external services see {@link AllTests}.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
@RunWith(Suite.class)
@SuiteClasses({ ApplicationTest.class, InternalIntegrationTest.class,
		AcceptUnitTest.class })
public class TravisTests {
	//
}