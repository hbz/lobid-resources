package controllers;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.typesafe.config.Config;

public class Application extends Controller {
	public final static Config CONFIG = com.typesafe.config.ConfigFactory
			.parseFile(new java.io.File("conf/application.conf")).resolve();

	public static Result index() {
		final String query = "Buch";
		final String url = url(query);
		final String result = call(url);
		return ok(index.render(CONFIG, query, url, result));
	}

	public static String url(String query) {
		final String template = "%s/resource?set=%s&q=%s&format=short";
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
