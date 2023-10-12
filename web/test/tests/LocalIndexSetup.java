/* Copyright 2017 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package tests;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import controllers.resources.LocalIndex;
import controllers.resources.Search;

/**
 * Setup for the alma fix search tests. Creates a local ES index with test data.
 *
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
public abstract class LocalIndexSetup {

	private static LocalIndex index;
	private static final String TEST_CONFIG =
		"../src/main/resources/alma/index-config.json";
	private static final String TEST_DATA = "../src/test/resources/alma-fix";

	@BeforeClass
	public static void setup() {
		index = new LocalIndex(TEST_CONFIG,TEST_DATA,null);
		Search.elasticsearchClient = index.getNode().client();
	}

	@AfterClass
	public static void down() {
		index.shutdown();
		Search.elasticsearchClient = null;
	}
}
