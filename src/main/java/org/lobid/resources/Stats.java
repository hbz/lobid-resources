/* Copyright 2015-2018 hbz. Licensed under the EPL 2.0 */

package org.lobid.resources;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.framework.DefaultStreamReceiver;
import org.culturegraph.mf.framework.StreamReceiver;
import org.culturegraph.mf.framework.annotations.Description;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

/**
 * Sums and sorts occurrences of a field. Facultatively also occurrences of the
 * values of that field may be output.
 * 
 * @author Pascal Christoph (dr0i)
 * @author Fabian Steeg (fsteeg)
 * 
 */
@Description("Sorted field statistics. May have appended a list of all values which "
		+ "are part of a field, sorted by their occurrences. The parameter 'filename' defines the place to store the"
		+ " stats on disk.")
@In(StreamReceiver.class)
@Out(Void.class)
public final class Stats extends DefaultStreamReceiver {
	private static final Logger LOG =
			LogManager.getLogger(DefaultStreamReceiver.class);
	private final HashMap<String, HashMap<String, Integer>> occurrenceOfMultipleFieldsMap =
			new HashMap<>();
	// stores how many records have that field at least once
	private static HashMap<String, Integer> occurrenceTmpMap = new HashMap<>();

	final HashMap<String, Integer> occurrenceOnceMap = new HashMap<>();

	final HashMap<String, HashMap<String, Integer>> fieldValueMap =
			new HashMap<>();
	private String filename;
	private int processedRecords;
	private static FileWriter textileWriter;

	/**
	 * Default constructor
	 */
	public Stats() {
		this.filename = "statistics.csv";
	}

	/**
	 * Sets the filename for writing the statistics.
	 * 
	 * @param filename the filename
	 */
	public void setFilename(final String filename) {
		this.filename = filename;
	}

	/**
	 * Handy when running as a test.
	 * 
	 */
	public void removeTestFile() {
		new File(this.filename).deleteOnExit();
	}

	/**
	 * Counts occurrences of fields. If field name starts with "log:", also the
	 * values and their occurrences are counted.
	 */
	@Override
	public void literal(String key, final String value) {
		if (key.startsWith("log:")) {
			key = key.replaceFirst("log: ", "");
			storeValuesOfField(fieldValueMap, key, value);
		}
		occurrenceTmpMap.put(key,
				(occurrenceTmpMap.containsKey(key) ? occurrenceTmpMap.get(key) : 0)
						+ 1);
	}

	private static void storeValuesOfField(
			HashMap<String, HashMap<String, Integer>> mapInMap, String key,
			final String value) {
		if (mapInMap.containsKey(key)) {
			if (mapInMap.get(key).get(value) != null)
				mapInMap.get(key).put(value, mapInMap.get(key).get(value) + 1);
			else
				mapInMap.get(key).put(value, 1);
		} else {
			HashMap<String, Integer> valueOfField = new HashMap<>();
			valueOfField.put(value, 1);
			mapInMap.put(key, valueOfField);
		}
	}

	/**
	 * Resets some temporary data.
	 * 
	 * @see org.culturegraph.mf.framework.DefaultStreamReceiver#startRecord(java.lang.
	 *      String)
	 */
	@Override
	public void startRecord(final String identifier) {
		occurrenceTmpMap = new HashMap<>();
	}

	/**
	 * Store processed records. Stores if a field appears at least once a time for
	 * a record.
	 * 
	 * @see org.culturegraph.mf.framework.DefaultStreamReceiver#endRecord()
	 */
	@Override
	public void endRecord() {
		processedRecords++;
		occurrenceTmpMap.forEach((k, v) -> {
			occurrenceOnceMap.put(k,
					(occurrenceOnceMap.containsKey(k) ? occurrenceOnceMap.get(k) : 0)
							+ 1);
			storeValuesOfField(occurrenceOfMultipleFieldsMap, k, String.valueOf(v));
		});
	}

	@Override
	public void closeStream() {
		try {
			writeTextileMappingTable(sortedByValuesDescending(occurrenceOnceMap),
					new File(this.filename));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void writeTextileMappingTable(
			final List<Entry<String, Integer>> occurrenceEntries,
			final File textileMappingFile) throws IOException {
		final StringBuilder textileBuilder = new StringBuilder(
				"*field*|*frequency*| facultative: different values sorted by occurrence , separated with commata*|\n");
		LOG.info("Log statistics of Field - Frequency or comma separated values");
		textileBuilder.append("\n|processed records|" + processedRecords + "||\n");
		createCsv(occurrenceEntries, textileBuilder);
		textileWriter = new FileWriter(textileMappingFile);
		try {
			textileWriter.write(textileBuilder.toString());
			textileWriter.flush();
		} finally {
			textileWriter.close();
			LOG.info("Wrote statistics to " + textileMappingFile.getName());
		}
	}

	private <T, I> void createCsv(final List<Entry<String, Integer>> occurrence,
			final StringBuilder textileBuilder) {
		for (Entry<String, Integer> e : occurrence) {
			LOG.info(e.getKey());
			textileBuilder.append(String.format(
					"\nField: %s|\nAt least once in a record: %s|\nFrequency of iteration of field: %s\nFrequency and value of field:%s\n",
					e.getKey(),
					e.getValue() + "/" + processedRecords + " <=> "
							+ (int) ((double) e.getValue() / (double) processedRecords * 100)
							+ "%",
					appendAllValuesOfField(occurrenceOfMultipleFieldsMap, e.getKey()),
					appendAllValuesOfField(fieldValueMap, e.getKey())));
		}
	}

	private static String appendAllValuesOfField(
			HashMap<String, HashMap<String, Integer>> mapInMap, String key) {
		StringBuilder sb = new StringBuilder();
		if (mapInMap.containsKey(key)) {
			sortedByValuesDescending(mapInMap.get(key)).forEach(e -> {
				LOG.info(e.getKey());
				sb.append(String.format("\n%s \t \"%s\",", e.getValue(), e.getKey()));
			});
		}
		return sb.length() > 1
				? sb.replace(sb.length() - 1, sb.length() + 1, "|").toString() : "|";
	}

	private static List<Entry<String, Integer>> sortedByValuesDescending(
			HashMap<String, Integer> map) {
		final List<Entry<String, Integer>> entries =
				new ArrayList<>(map.entrySet());
		Collections.sort(entries, new Comparator<Entry<String, Integer>>() {
			@Override
			public int compare(final Entry<String, Integer> entry1,
					final Entry<String, Integer> entry2) {
				// compare second to first for descending order:
				return entry2.getValue().compareTo(entry1.getValue());
			}
		});
		return entries;
	}

}
