
/* Copyright 2017, hbz. Licensed under the Eclipse Public License 1.0 */

import controllers.resources.Index;
import controllers.resources.LocalIndex;
import play.Application;
import play.GlobalSettings;

/**
 * Application global settings.
 * 
 * See https://www.playframework.com/documentation/2.4.x/JavaGlobal
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class Global extends GlobalSettings {

	private LocalIndex localIndex = null;

	@Override
	public void onStart(Application app) {
		super.onStart(app);
		if (Index.CLUSTER_HOSTS.isEmpty() && !app.isTest()) {
			localIndex = new LocalIndex();
			Index.elasticsearchClient = localIndex.getNode().client();
		}
	}

	@Override
	public void onStop(Application app) {
		if (localIndex != null) {
			localIndex.shutdown();
		}
		super.onStop(app);
	}
}
