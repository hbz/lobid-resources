/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import play.api.templates.Html;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.typesafe.config.Config;

public class Application extends Controller {
	public final static Config CONFIG = com.typesafe.config.ConfigFactory
			.parseFile(new java.io.File("conf/application.conf")).resolve();
	static Form<String> queryForm = Form.form(String.class);

	static String query = "";
	static String url = url(query);
	static String result = call(url);

	public static Result index() {
		return ok(views.html.index.render(CONFIG, queryForm, url, result));
	}

	public static Result query() {
		final Form<String> filledForm = queryForm.bindFromRequest();
		if (filledForm.hasErrors()) {
			Html html = views.html.index.render(CONFIG, filledForm, null, null);
			return badRequest(html);
		} else {
			query = filledForm.data().get("query");
			url = url(query);
			result = call(url);
			return redirect(routes.Application.index());
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
