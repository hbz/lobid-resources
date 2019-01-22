package tests;

import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.resources.Lobid;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.mvc.Http;

public class HbzfixTests {
	private JsonNode mappingNode;

	@Test
	public void test() {
		running(fakeApplication(), () -> {
			Promise<JsonNode> promise =
					WS.url("http://indexdev.hbz-nrw.de/_es2/hbzfix/_search").get()
							.map(response -> response.getStatus() == Http.Status.OK
									? response.asJson() : Json.newObject());
			promise.onRedeem(jsonResponse -> {
				System.out.println(jsonResponse);
			});
			promise.get(Lobid.API_TIMEOUT);
		});
	}

	@Test
	public void indices() {
		Map<String, Map<String, List<String>>> indexMap = new HashMap<>();
		running(fakeApplication(), () -> {
			Promise<JsonNode> promise =
					WS.url("http://indexdev.hbz-nrw.de/_es2/_stats").get()
							.map(response -> response.getStatus() == Http.Status.OK
									? response.asJson() : Json.newObject());
			JsonNode jsonNode = promise.get(Lobid.API_TIMEOUT);
			jsonNode.get("indices").fields().forEachRemaining(field -> {
				String index = field.getKey();
				Map<String, List<String>> forIndex =
						indexMap.getOrDefault(index, new HashMap<>());
				Promise<JsonNode> mappings = WS
						.url(String.format("http://indexdev.hbz-nrw.de/_es2/%s/_mapping",
								index))
						.get().map(response -> response.getStatus() == Http.Status.OK
								? response.asJson() : Json.newObject());
				mappingNode = mappings.get(Lobid.API_TIMEOUT);
				mappingNode.get(index).get("mappings").fields()
						.forEachRemaining(mappingField -> {
							String t = mappingField.getKey();
							List<String> forType =
									forIndex.getOrDefault(t, new ArrayList<>());
							mappingField.getValue().get("properties").fields()
									.forEachRemaining(f -> {
										String fieldName = f.getKey();
										forType.add(fieldName);
									});
							forIndex.put(t, forType);
							Collections.sort(forType);
						});
				indexMap.put(index, forIndex);
			});
		});
		try (FileWriter fw = new FileWriter("test/tests/fields-hbzfix.json")) {
			fw.write(Json.prettyPrint(Json.toJson(indexMap)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
