/* Copyright 2018 Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.Description;
import org.culturegraph.mf.framework.annotations.FluxCommand;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * <p>
 * Reads comma and line separated json records residing in a json array from a
 * {@code Reader} and splits it into individual records. To be consumed from
 * {@code ElasticsearchIndexer}. ES-internal ID is parsed from json.
 * </p>
 *
 * @author Pascal Christoph (dr0i)
 *
 */
@Description("Reads data from a Reader and splits it into individual records")
@In(Reader.class)
@Out(HashMap.class)
@FluxCommand("as-elasticsearch-json-records")
public final class RecordReaderEnhanced
		extends DefaultObjectPipe<Reader, ObjectReceiver<HashMap<String, String>>> {
	private static final int BUFFER_SIZE = 1024 * 1024 * 16;
	HashMap<String, String> json = new HashMap<>(BUFFER_SIZE);
	/**
	 * Sets the name of the index.
	 */
	public static String INDEX = "entityfacts";
	private final static String TYPE = "entityfacts";
	private static final Logger LOG =
			LogManager.getLogger(RecordReaderEnhanced.class);
	private static ObjectMapper mapper = new ObjectMapper();
	private static int CNT_LOG_ALL = 5000;
	private static int cnt = 0;

	@Override
	public void onSetReceiver() {
		System.out.println(
				"Every dot of the process bar represents " + CNT_LOG_ALL + " records");
	}

	@SuppressWarnings("null")
	@Override
	public void process(final Reader reader) {
		assert!isClosed();
		String line = null;
		try {
			json = new HashMap<>(BUFFER_SIZE);
			final BufferedReader lineReader = new BufferedReader(reader, BUFFER_SIZE);
			line = lineReader.readLine();
			while (line != null) {
				toJsonHashMapAndProcess(line.substring(1, line.length()));
				line = lineReader.readLine();
			}
		} catch (Exception e) {
			LOG.warn(e.getMessage() + "\n" + line);
			LOG.info("Try again, maybe it's the last record of an array");
			try {
				toJsonHashMapAndProcess(line.replaceFirst("\\]$", ""));
			} catch (Exception e1) {
				LOG.warn(e.getMessage()
						+ " ... after tried again it fails nevertheless:\n" + line);
			}
		}
	}

	private void toJsonHashMapAndProcess(String jsonStr) {
		try {
			ObjectNode node = mapper.readValue(jsonStr, ObjectNode.class);
			json.put(ElasticsearchIndexer.Properties.INDEX.getName(), INDEX);
			json.put(ElasticsearchIndexer.Properties.TYPE.getName(), TYPE);
			json.put(ElasticsearchIndexer.Properties.ID.getName(),
					node.findValue("describedBy").findValue("@id").asText()
							.replaceFirst("http://hub.culturegraph.org/entityfacts/", ""));
			json.put(ElasticsearchIndexer.Properties.GRAPH.getName(), jsonStr);
		} catch (IOException e) {
			e.printStackTrace();
		}
		getReceiver().process(json);
		cnt++;
		if (cnt % CNT_LOG_ALL == 0)
			System.out.print(".");
	}

	@Override
	public void onCloseStream() {
		System.out.println("Records processed: " + String.valueOf(cnt));
	}
}