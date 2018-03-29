/* Copyright 2017 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package tests;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import controllers.resources.LocalIndex;
import controllers.resources.Search;

/**
 * Setup for the search tests. Creates a local ES index with test data.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
public abstract class LocalIndexSetup {

	private static LocalIndex index;

	@BeforeClass
	public static void setup() {
		index = new LocalIndex();
		Search.elasticsearchClient = index.getNode().client();
	}

	@AfterClass
	public static void down() {
		index.shutdown();
		Search.elasticsearchClient = null;
	}
}
