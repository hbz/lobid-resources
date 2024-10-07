package tests;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.running;

import org.junit.Test;

import play.Application;
import play.libs.Json;
import play.mvc.Result;

/**
 * Test suggestion responses (see {@link controllers.resources.Application})
 */
@SuppressWarnings("javadoc")
public class SuggestionsTest extends LocalIndexSetup {

	@Test
	public void suggestionsWithoutCallback() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application, fakeRequest(GET,
					"/resources/search?q=*&filter=type:Book&format=json:title,contribution"));
			assertNotNull("We have a result", result);
			assertThat(result.contentType(), equalTo("application/json"));
			String content = contentAsString(result);
			assertNotNull("We can parse the result as JSON", Json.parse(content));
			assertThat(content,
					allOf(//
							containsString("label"), //
							containsString("id"), //
							containsString("category")));
			assertTrue("We used both given fields for any of the labels",
					Json.parse(content).findValues("label").stream()
							.anyMatch(label -> label.asText().contains(" | ")));
		});

	}

	@Test
	public void suggestionsWithCallback() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application, fakeRequest(GET,
					"/resources/search?q=*&filter=type:Book&format=json:title&callback=test"));
			assertNotNull("We have a result", result);
			assertThat(result.contentType(), equalTo("application/javascript"));
			assertThat(contentAsString(result),
					allOf(containsString("test("), // callback
							containsString("label"), containsString("id"),
							containsString("category")));
		});
	}

	@Test
	public void suggestionsCorsHeader() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application,
					fakeRequest(GET, "/resources/search?q=*&format=json:title"));
			assertNotNull("We have a result", result);
			assertThat(result.header("Access-Control-Allow-Origin"), equalTo("*"));
		});

	}

	@Test
	public void suggestionsTemplate() {
		Application application = fakeApplication();
		running(application, () -> {
			String format = "json:title,ab_startDate+als_edition";
			Result result = route(application, fakeRequest(GET,
					"/resources/search?q=*&filter=type:Book&format=" + format));
			assertNotNull("We have a result", result);
			assertThat(result.contentType(), equalTo("application/json"));
			String content = contentAsString(result);
			assertNotNull("We can parse the result as JSON", Json.parse(content));
			assertTrue(
					"We replaced the field names in the template with their values",
					Json.parse(content).findValues("label").stream()
							.anyMatch(label -> label.asText().contains("als ")));
		});
	}

	@Test
	public void suggestionsTemplateMultiValues() {
		Application application = fakeApplication();
		running(application, () -> {
			String format = "json:title,contribution,about_subject";
			Result result = route(application,
					fakeRequest(GET,
							"/resources/search?q=Volksschulwesens&filter=type:Book&format="
									+ format));
			assertNotNull("We have a result", result);
			assertThat(result.contentType(), equalTo("application/json"));
			String content = contentAsString(result);
			assertNotNull("We can parse the result as JSON", Json.parse(content));
			assertThat("Multi-values use consistent delimiter", content, allOf(
					containsString("Handwörterbuch des Volksschulwesens"),
					containsString("about Erziehung, Bildung, Unterricht; Volksschule")));
		});
	}

	@Test
	public void suggestionsArePrettyPrinted() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application,
					fakeRequest(GET, "/resources/search?q=*&format=json:suggest"));
			assertNotNull(result);
			assertThat(result.contentType(), equalTo("application/json"));
			assertThat(contentAsString(result), containsString("}, {\n"));
		});
	}

}
