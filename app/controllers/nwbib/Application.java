/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.nwbib;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.typesafe.config.Config;

public class Application extends Controller {
	private static final File FILE = new java.io.File("conf/nwbib.conf");
	public final static Config CONFIG = com.typesafe.config.ConfigFactory
			.parseFile(
					FILE.exists() ? FILE : new java.io.File(
							"modules/nwbib/conf/nwbib.conf")).resolve();
	static Form<String> queryForm = Form.form(String.class);

	static String query = "";
	static String url = url(query);
	static String result = call(url);

	public static Result index() {
		return ok(views.html.nwbib_index.render(CONFIG, queryForm, url, result));
	}

	public static Result query() {
		final Form<String> filledForm = queryForm.bindFromRequest();
		if (filledForm.hasErrors()) {
			return badRequest(views.html.nwbib_index.render(CONFIG, filledForm,
					null, null));
		} else {
			query = filledForm.data().get("query");
			url = url(query);
			result = call(url);
			return controllers.nwbib.Application.index();
		}
	}

	public static String url(String query) {
		final String template = "%s/resource?set=%s&format=short&q=%s";
		return String.format(template, CONFIG.getString("nwbib.api"),
				CONFIG.getString("nwbib.set"), query);
	}

	public static String call(final String url) {
		try {
			final URLConnection connection = new URL(url).openConnection();
			connection.setRequestProperty("Accept", "application/json");
			return CharStreams.toString(new InputStreamReader(connection
					.getInputStream(), Charsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
