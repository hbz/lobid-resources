/* Copyright 2016,2017 Pascal Christoph. Licensed under the EPL 2.0 */

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Main test suite for all unit tests. The order of execution of the test
 * classes is important for the output of one is the input of the other.
 *
 * @author Pascal Christoph (dr0i)
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    org.lobid.resources.AlmaMarc21XmlToLobidJsonMetafixTest.class,
    org.lobid.resources.CulturegraphXmlFilterHbzRvkToTsvTest.class,
    org.lobid.resources.CulturegraphXmlFilterHbzToJsonTest.class})

public final class UnitTests {
    /* Suite class, groups tests via annotation above */
}
