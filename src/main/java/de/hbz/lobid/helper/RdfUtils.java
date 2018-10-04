/* Copyright (c) 2015 "hbz". Licensed under the EPL 2.0 */

package de.hbz.lobid.helper;

import java.io.InputStream;
import java.io.StringWriter;

import org.openrdf.model.Graph;
import org.openrdf.model.Statement;
import org.openrdf.model.impl.TreeModel;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.Rio;
import org.openrdf.rio.helpers.StatementCollector;

/**
 * @author Jan Schnasse
 *
 */
public class RdfUtils {
	/**
	 * @param inputStream an Input stream containing rdf data
	 * @param inf the rdf format
	 * @param baseUrl see sesame docu
	 * @return a Graph representing the rdf in the input stream
	 */
	public static Graph readRdfToGraph(final InputStream inputStream,
			final RDFFormat inf, final String baseUrl) {
		try {
			final RDFParser rdfParser = Rio.createParser(inf);
			final org.openrdf.model.Graph myGraph = new TreeModel();
			final StatementCollector collector = new StatementCollector(myGraph);
			rdfParser.setRDFHandler(collector);
			rdfParser.parse(inputStream, baseUrl);
			return myGraph;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param in a rdf input stream
	 * @param inf the rdf format of the input stream
	 * @param outf the output format
	 * @param baseUrl usually the url of the resource
	 * @return a string representation
	 */
	public static String readRdfToString(InputStream in, RDFFormat inf,
			RDFFormat outf, String baseUrl) {
		Graph myGraph = null;
		myGraph = readRdfToGraph(in, inf, baseUrl);
		return graphToString(myGraph, outf);
	}

	/**
	 * Transforms a graph to a string.
	 * 
	 * @param myGraph a sesame rdf graph
	 * @param outf the expected output format
	 * @return a rdf string
	 */
	public static String graphToString(Graph myGraph, RDFFormat outf) {
		StringWriter out = new StringWriter();
		RDFWriter writer = Rio.createWriter(outf, out);
		try {
			writer.startRDF();
			for (Statement st : myGraph) {
				writer.handleStatement(st);
			}
			writer.endRDF();
		} catch (RDFHandlerException e) {
			throw new RuntimeException(e);
		}
		return out.getBuffer().toString();
	}
}
