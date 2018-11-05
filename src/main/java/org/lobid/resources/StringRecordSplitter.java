package org.lobid.resources;

import java.io.BufferedReader;
import java.io.Reader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.culturegraph.mf.framework.DefaultObjectPipe;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.annotations.Description;
import org.culturegraph.mf.framework.annotations.FluxCommand;
import org.culturegraph.mf.framework.annotations.In;
import org.culturegraph.mf.framework.annotations.Out;

/*
 * Copyright 2018 Pascal Christoph
 *
 * Licensed under the Apache License, Version 2.0 the "License";
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
		assert!isClosed();
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