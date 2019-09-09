/* Copyright 2019 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package de.hbz.lobid.helper;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * Creates nwbib maps used by morph.
 * 
 * @author Pascal Christoph (dr0i)
 *
 */
public final class CreateWikidataNwbibMaps {
	private static final Logger LOG =
			LogManager.getLogger(CreateWikidataNwbibMaps.class);
	private static final String WARN =
			"will not renew the map but going with the old one";
	private static final Model MODEL = ModelFactory.createDefaultModel();
	private static final Property FOCUS =
			MODEL.createProperty("http://xmlns.com/foaf/0.1/focus");
	private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
	private static final Property PREFLABEL =
			MODEL.createProperty(SKOS + "prefLabel");
	private static final Property NOTATION =
			MODEL.createProperty(SKOS + "notation");
	private static final File TEST_FN =
			new File("src/main/resources/nwbib-spatial.tsv");

	/**
	 * Creates a tsv using nwbib-spatial.ttl for a morph map.
	 * 
	 * @param args nope
	 */
	public static void main(String... args) {

		try {
			MODEL
					.read(new InputStreamReader(
							new URL(
									"https://github.com/hbz/lobid-vocabs/raw/master/nwbib/nwbib-spatial.ttl")
											.openConnection().getInputStream(),
							StandardCharsets.UTF_8), null, "TTL");
		} catch (IOException e) {
			LOG.warn("Couldn't lookup nwbib-spatial.ttl," + WARN, e);
			return;
		}
		StringBuilder sb = new StringBuilder();
		ResIterator it = MODEL.listSubjects();
		while (it.hasNext()) {
			Resource res = it.next();
			if (res.hasProperty(FOCUS))
				sb.append(res + "\t"
						+ res.getProperty(PREFLABEL).getLiteral().getLexicalForm() + "|"
						+ (res.hasProperty(NOTATION)
								? res.getProperty(NOTATION).getLiteral().getLexicalForm() : "")
						+ "|" + res.getProperty(FOCUS).getObject() + "\n");
		}
		if (sb.length() < 3000) {
			LOG.warn("nwbib-spatial.ttl not large enough." + WARN);
		} else {
			try {
				FileUtils.writeStringToFile(TEST_FN, sb.toString(),
						StandardCharsets.UTF_8);
				LOG.info(
						"Success: created 'nwbib-spatial.tsv from skos file at lobid.org'");
			} catch (IOException e) {
				LOG.warn("Couldn't write file." + WARN, e);
			}
		}
	}
}
