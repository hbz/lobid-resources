/* Copyright 2013,2016 Pascal Christoph, hbz. Licensed under the EPL 2.0 */

package org.lobid.resources;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.NoSuchElementException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.metafacture.framework.MetafactureException;
import org.metafacture.framework.annotations.Description;
import org.metafacture.framework.annotations.In;
import org.metafacture.framework.annotations.Out;
import org.metafacture.framework.helpers.DefaultObjectReceiver;
import org.metafacture.xml.FilenameExtractor;


/**
 * A sink, writing triples into files. The filenames are constructed from the
 * literal of an given property.
 *
 * @author Pascal Christoph (dr0i)
 */
@Description("Writes the object value of an RDF model into a file. Default serialization is 'NTRIPLES'. The filename is "
		+ "constructed from the literal of an given property (recommended properties are identifier)."
		+ " Variable are " + "- 'target' (determining the output directory)"
		+ "- 'property' (the property in the RDF model. The object of this property"
		+ " will be the main part of the file's name.) "
		+ "- 'startIndex' ( a subfolder will be extracted out of the filename. This marks the index' beginning )"
		+ "- 'stopIndex' ( a subfolder will be extracted out of the filename. This marks the index' end )"
		+ "- 'serialization (e.g. one of 'NTRIPLES', 'TURTLE', 'RDFXML','RDFJSON'")
@In(Model.class)
@Out(Void.class)
public final class RdfModelFileWriter extends DefaultObjectReceiver<Model>
		implements FilenameExtractor {
	private static final Logger LOG =
			LogManager.getLogger(RdfModelFileWriter.class);

	private FilenameUtil filenameUtil = new FilenameUtil();
	private Lang serialization;

	/**
	 * Default constructor
	 */
	public RdfModelFileWriter() {
		setProperty("http://purl.org/dc/terms/identifier");
		setFileSuffix("nt");
		setSerialization("NTRIPLES");
	}

	@Override
	public String getEncoding() {
		return filenameUtil.getEncoding();
	}

	@Override
	public void setEncoding(final String encoding) {
		filenameUtil.setEncoding(encoding);
	}

	@Override
	public void setTarget(final String target) {
		filenameUtil.setTarget(target);
	}

	@Override
	public void setProperty(final String property) {
		filenameUtil.setProperty(property);
	}

	@Override
	public void setFileSuffix(final String fileSuffix) {
		filenameUtil.setFileSuffix(fileSuffix);
	}

	@Override
	public void setStartIndex(final int startIndex) {
		filenameUtil.setStartIndex(startIndex);
	}

	@Override
	public void setEndIndex(final int endIndex) {
		filenameUtil.setEndIndex(endIndex);
	}

	/**
	 * Sets the rdf serialization language.
	 *
	 * @param serialization the language to be serialized
	 */
	public void setSerialization(final String serialization) {
		this.serialization = RDFLanguages.nameToLang(serialization);
	}

	@Override
	public void process(final Model model) {
		String identifier = null;
		try {
			identifier = model
					.listObjectsOfProperty(model.createProperty(filenameUtil.getProperty()))
					.next().toString();
			LOG.debug("Going to store identifier=" + identifier);
		} catch (NoSuchElementException e) {
			LOG.warn(
					"No identifier => cannot derive a filename for " + model.toString());
			return;
		}

		String directory = identifier;
		if (directory.length() >= filenameUtil.getEndIndex()) {
			directory =
					directory.substring(filenameUtil.getStartIndex(), filenameUtil.getEndIndex());
		}
		final String file = FilenameUtils.concat(filenameUtil.getTarget(),
				FilenameUtils.concat(directory + File.separator,
						identifier + "." + filenameUtil.getFileSuffix()));
		LOG.debug("Write to " + file);
		filenameUtil.ensurePathExists(new File(file));

		try (
				final Writer writer = new OutputStreamWriter(new FileOutputStream(file),
						filenameUtil.getEncoding())) {
			final StringWriter tripleWriter = new StringWriter();
			RDFDataMgr.write(tripleWriter, model, this.serialization);
			IOUtils.write(tripleWriter.toString(), writer);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new MetafactureException(e);
		}
	}

}
