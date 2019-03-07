/* Copyright 2018 Pascal Christoph. Licensed under the EPL 2.0 */

package org.lobid.resources;

import java.io.BufferedReader;
import java.io.Reader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.metafacture.framework.FluxCommand;
import org.metafacture.framework.ObjectReceiver;
import org.metafacture.framework.annotations.Description;
import org.metafacture.framework.annotations.In;
import org.metafacture.framework.annotations.Out;
import org.metafacture.framework.helpers.DefaultObjectPipe;

/**
 * <p>
 * Reads line separated strings and append them to a new a bigger new string
 * (aka "record"). Hitting a defined pattern this record is emitted and a new
 * one is started.
 * </p>
 *
 * @author Pascal Christoph (dr0i)
 *
 */
@Description("Reads data from a Reader and splits it into individual records")
@In(java.io.Reader.class)
@Out(String.class)
@FluxCommand("rec")
public final class StringRecordSplitter
		extends DefaultObjectPipe<Reader, ObjectReceiver<String>> {
	private static final int BUFFER_SIZE = 1024 * 1024 * 16;

	private static final Logger LOG =
			LogManager.getLogger(StringRecordSplitter.class);
	static String NEW_RECORD_MARKER;

	/**
	 * @param regex defines a marker. When this line is hit a new record is
	 *          emitted.
	 */
	public StringRecordSplitter(String regex) {
		NEW_RECORD_MARKER = regex;
	}

	@Override
	public void process(final Reader reader) {
		assert !isClosed();
		StringBuilder record = new StringBuilder(4096 * 12);
		String line = null;
		try {
			final BufferedReader lineReader = new BufferedReader(reader, BUFFER_SIZE);
			line = lineReader.readLine();
			if (line != null) {
				// first line can't be the end marker of a record
				record.append(line);
				line = lineReader.readLine();
				// read all lines of the file
				while (line != null) {
					if (line.matches(NEW_RECORD_MARKER)) {
						getReceiver().process(record.toString());
						record = new StringBuilder(4096 * 12);
					}
					record.append("\n" + line);
					line = lineReader.readLine();
				}
				// the last line isn't a record's starting marker, so emit that last
				// record now
				getReceiver().process(record.toString());
			}
		} catch (Exception e) {
			LOG.warn(e.getMessage() + "\n" + line);
		}
	}
}