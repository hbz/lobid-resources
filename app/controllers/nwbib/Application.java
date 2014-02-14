/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers.nwbib;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.nwbib_index;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Application extends Controller {
	private static final File FILE = new File("conf/nwbib.conf");
	public final static Config CONFIG = ConfigFactory.parseFile(
			FILE.exists() ? FILE : new File("modules/nwbib/conf/nwbib.conf"))
			.resolve();
	static Form<String> queryForm = Form.form(String.class);

	public static Result index(final String q) {
		final Form<String> form = queryForm.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(nwbib_index.render(CONFIG, form, null, null, q));
		} else {
			final String query = form.data().get("query");
			final String url = url(query != null ? query : q);
			final String result = call(url);
			return ok(nwbib_index.render(CONFIG, form, url, result, q));
		}
	}

	public static String url(String query) {
		final String template = "%s/resource?set=%s&format=full&from=0&size=50&q=%s";
		try {
			return String.format(template, CONFIG.getString("nwbib.api"),
					CONFIG.getString("nwbib.set"),
					URLEncoder.encode(query, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return "";
	}

	public static String call(final String url) {
		try {
			final URLConnection connection = new URL(url).openConnection();
			return CharStreams.toString(new InputStreamReader(connection
					.getInputStream(), Charsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
