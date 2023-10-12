/* Copyright (c) 2016 "hbz". Licensed under the EPL 2.0 */

package de.hbz.lobid.helper;

import java.util.Collection;
import java.util.Map;

/**
 * @author Jan Schnasse
 *
 */
public interface EtikettMakerInterface {

	/**
	 * @return a map with a json-ld context
	 */
	Map<String, Object> getContext();

	/**
	 * @param uri the uri
	 * @return an etikett object contains uri, icon, label, jsonname,
	 *         referenceType
	 */
	Etikett getEtikett(String uri);

	/**
	 * @param name the json name
	 * @return an etikett object contains uri, icon, label, jsonname,
	 *         referenceType
	 */
	Etikett getEtikettByName(String name);

	/**
	 * @return a list of all Etiketts
	 */
	Collection<Etikett> getValues();

	/**
	 * @return true if the implementor provides etiketts for all kind of values
	 *         (uris on object position). false if the implementor provides
	 *         etiketts for fields (uris in predicate position) only.
	 */
	boolean supportsLabelsForValues();

	/**
	 * @return the idAlias is used to substitute all occurrences of "\@id"
	 */
	public String getIdAlias();

	/**
	 * @return the typeAlias is used to substitute all occurrences of "\@type"
	 */
	public String getTypeAlias();

	/**
	 * @return returns a json key that is used to store label info
	 */
	public String getLabelKey();

	/**
	 * @return filename of the jsonld-context
	 */
	public String getContextLocation();

	/**
	 * Sets the filename of the jsonld-context.
	 *
	 * @param contextFname the filename of the jsonld-context
	 */
	public void setContextLocation(String contextFname);

}
