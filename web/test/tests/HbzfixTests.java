package tests;

import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.resources.Lobid;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.mvc.Http;

public class HbzfixTests {
	@Test
	public void test() {
		running(fakeApplication(), () -> {
			Promise<JsonNode> promise =
					WS.url("http://indexdev.hbz-nrw.de/_es2/hbzfix/_search").get()
							.map(response -> response.getStatus() == Http.Status.OK
									? response.asJson()
									: Json.newObject());
			promise.onRedeem(jsonResponse -> {
				System.out.println(jsonResponse);
			});
			promise.get(Lobid.API_TIMEOUT);
		});
	}
}
