/* Copyright 2015-2017 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import play.Logger;
import play.Play;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

/**
 * Test nasty input strings using data from
 * https://github.com/minimaxir/big-list-of-naughty-strings
 *
 * See http://www.playframework.com/documentation/2.3.x/JavaTest
 */
@SuppressWarnings("javadoc")
@RunWith(value = Parameterized.class)
public class InputStringsTest extends LocalIndexSetup {

	private final String input;

	/**
	 * @return The data to use for this parameterized test (test is executed once
	 *         for every element, which is passed to the constructor of this test)
	 */
	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		List<Object[]> strings = new ArrayList<>();
		running(testServer(3333), () -> {
			JsonNode data =
					Json.parse(Play.application().resourceAsStream("blns.json"));
			for (JsonNode n : data) {
				strings.add(new String[] { n.textValue() });
			}
		});
		return strings;
	}

	/**
	 * @param input the input string to search for
	 */
	public InputStringsTest(final String input) {
		this.input = input;
		System.out.println(
				String.format("Testing if calling searching for '%s' works", input));
	}

	@Before
	public void setUp() throws Exception {
		Map<String, String> flashData = Collections.emptyMap();
		Map<String, Object> argData = Collections.emptyMap();
		play.api.mvc.RequestHeader header = mock(play.api.mvc.RequestHeader.class);
		Http.Request request = mock(Http.Request.class);
		Http.Context context =
				new Http.Context(2L, header, request, flashData, flashData, argData);
		Http.Context.current.set(context);
	}

	@Test
	public void test() {
		running(testServer(3333), () -> {
			String uri = controllers.resources.Application.query(input, "", "", "",
					"", "", "", "", "", 0, 10, "", "", "", "", "", "", "", "", "").toString();
			try {
				URLDecoder.decode(input, StandardCharsets.UTF_8.name());
			} catch (IllegalArgumentException | UnsupportedEncodingException x) {
				Logger.warn("Can't decode: " + input, x);
				return;
			}
			Result result = Helpers.route(new Http.RequestBuilder().uri(uri)
					.method(Helpers.GET).path("/").bodyForm(ImmutableMap.of()));
			// we don't expect any server errors (see
			// https://en.wikipedia.org/wiki/List_of_HTTP_status_codes#5xx_Server_Error)
			assertThat(result.status() + "").matches("[^5]..");
		});
	}

}
