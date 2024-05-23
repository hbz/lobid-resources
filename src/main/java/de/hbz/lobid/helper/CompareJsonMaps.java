/* Copyright 2015 hbz, Pascal Christoph. Licensed under the EPL 2.0 */

package de.hbz.lobid.helper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;

/**
 * Builds a map with json paths as keys with aggregated values, thus comparing
 * to JsonNodes becomes easy. If a key ends with "Order" it is assumed that the
 * values must be in the given order, so this tests also ordered lists.
 * Successfully compared elements will be removed from the actual map, thus a
 * successful comparison leads to an empty 'actual' map (see last line).
 *
 * @author Pascal Christoph (dr0i)
 * @author Jan Schnasse
 *
 */
@SuppressWarnings("javadoc")
public final class CompareJsonMaps {
	final static Logger logger = LoggerFactory.getLogger(CompareJsonMaps.class);
	Stack<String> stack = new Stack<>();
	static final String JSON_LD_CONTEXT = "[@context";
	private static boolean IGNORE_CONTEXT = true;
	private static String filename1;
	private static String filename2;

	public static void main(String... args) {
		if (args.length < 2)
			CompareJsonMaps.logger.info(
					"Usage: <filename1> <filename2> {<false> if @context should be taken into acount}");
		filename1 = args[0];
		filename2 = args[1];
		CompareJsonMaps.logger
				.info("\n" + filename1 + " may be referenced in the logs as 'actual'\n"
						+ filename2 + " may be referenced as 'expected'");
		if (args.length >= 3 && args[2].equals("false"))
			IGNORE_CONTEXT = false;
		try {
			if (new CompareJsonMaps().writeFileAndTestJson(
					new ObjectMapper().readValue(new File(args[0]), JsonNode.class),
					new ObjectMapper().readValue(new File(args[1]), JsonNode.class)))
				CompareJsonMaps.logger.info("OK. The content is equal.");
			else
				CompareJsonMaps.logger.info("Sadeness. The content differs.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean writeFileAndTestJson(final JsonNode actual,
			final JsonNode expected) {
		// generated data to map
		final HashMap<String, String> actualMap = new HashMap<>();
		extractFlatMapFromJsonNode(actual, actualMap);
		// expected data to map
		final HashMap<String, String> expectedMap = new HashMap<>();
		extractFlatMapFromJsonNode(expected, expectedMap);
		CompareJsonMaps.logger.debug("\n##### remove good entries ###");
		Iterator<String> it = actualMap.keySet().iterator();
		removeContext(it);
		it = expectedMap.keySet().iterator();
		removeContext(it);
		for (final Entry<String, String> e : expectedMap.entrySet()) {
			CompareJsonMaps.logger.debug("Trying to remove " + e.getKey() + "...");
			if (!actualMap.containsKey(e.getKey())) {
				CompareJsonMaps.logger
						.warn("At least this element is missing in actual: " + e.getKey()
								+ "=" + e.getValue());
				return false;
			}
			if (e.getKey().endsWith("Order]")) {
				handleOrderedValues(actualMap, e);
			} else {
				handleUnorderedValues(actualMap, e);
			}
		}
		if (!actualMap.isEmpty()) {
			CompareJsonMaps.logger
					.warn("Fail - no Equality! These keys/values were NOT expected:");
			actualMap.forEach((key, val) -> CompareJsonMaps.logger
					.warn("KEY=" + key + " VALUE=" + val));
		} else
			CompareJsonMaps.logger.debug("Succeeded - resources are equal");
		return actualMap.size() == 0;
	}

	private static void removeContext(Iterator<String> it) {
		while (it.hasNext()) {
			String se = it.next();
			if (IGNORE_CONTEXT && se.startsWith(JSON_LD_CONTEXT))
				it.remove();
		}
	}

	private static void handleUnorderedValues(
			final HashMap<String, String> actualMap, final Entry<String, String> e) {
		if (checkIfAllValuesAreContainedUnordered(actualMap.get(e.getKey()),
				e.getValue())) {
			actualMap.remove(e.getKey());
			CompareJsonMaps.logger.debug("Removed " + e.getKey());
		} else {
			CompareJsonMaps.logger
					.debug("Missing/wrong: " + e.getKey() + ", will fail");
		}
	}

	private static void handleOrderedValues(
			final HashMap<String, String> actualMap, final Entry<String, String> e) {
		CompareJsonMaps.logger.debug("Test if proper order for: " + e.getKey());
		if (actualMap.containsKey(e.getKey())) {
			CompareJsonMaps.logger.trace("Existing as expected: " + e.getKey());
			if (e.getValue().equals(actualMap.get(e.getKey()))) {
				CompareJsonMaps.logger.trace(
						"Equality:\n" + e.getValue() + "\n" + actualMap.get(e.getKey()));
				actualMap.remove(e.getKey());
			} else
				CompareJsonMaps.logger.debug("...but not equal! Will fail");
		} else {
			CompareJsonMaps.logger.warn("Missing: " + e.getKey() + " , will fail");
		}
	}

	/**
	 * Construct a map with json paths as keys with aggregated values form json
	 * nodes.
	 *
	 * @param jnode the JsonNode which should be transformed into a map
	 * @param map the map constructed out of the JsonNode
	 */
	public void extractFlatMapFromJsonNode(final JsonNode jnode,
			final HashMap<String, String> map) {
		if (jnode.getNodeType().equals(JsonNodeType.OBJECT)) {
			final Iterator<Map.Entry<String, JsonNode>> it = jnode.fields();
			while (it.hasNext()) {
				final Map.Entry<String, JsonNode> entry = it.next();
				stack.push(entry.getKey());
				extractFlatMapFromJsonNode(entry.getValue(), map);
				stack.pop();
			}
		} else if (jnode.isArray()) {
			final Iterator<JsonNode> it = jnode.iterator();
			while (it.hasNext()) {
				extractFlatMapFromJsonNode(it.next(), map);
			}
		} else if (jnode.isValueNode()) {
			String value = jnode.toString();
			if (map.containsKey(stack.toString()))
				value = map.get(stack.toString()).concat("," + jnode.toString());
			map.put(stack.toString(), value);
			CompareJsonMaps.logger
					.trace("Stored this path as key into map:" + stack.toString(), value);
		}
	}

	/*
	 * Values may be an unorderd set.
	 */
	private static boolean checkIfAllValuesAreContainedUnordered(
			final String actual, final String expected) {
		CompareJsonMaps.logger
				.trace("\nActual   value: " + actual + "\nExpected value: " + expected);
		return valuesToSet(actual).equals(valuesToSet(expected));
	}

	private static Set<String> valuesToSet(final String values) {
		return Stream.of(values.substring(1, values.length() - 1).split("\",\""))
				.collect(Collectors.toSet());
	}
}
