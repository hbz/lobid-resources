import controllers.nwbib.Classification;
import play.Application;
import play.GlobalSettings;

/**
 * Application global settings. Start and stop Classification index.
 * 
 * See https://www.playframework.com/documentation/2.3.x/JavaGlobal
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class Global extends GlobalSettings {

	@Override
	public void onStart(Application app) {
		super.onStart(app);
		Classification.indexStartup();
	}

	@Override
	public void onStop(Application app) {
		Classification.indexShutdown();
		super.onStop(app);
	}
}