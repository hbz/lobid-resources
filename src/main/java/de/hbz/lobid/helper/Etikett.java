/*Copyright (c) 2015 "hbz"

This file is part of etikett.

etikett is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.hbz.lobid.helper;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author Jan Schnasse
 *
 */
@XmlRootElement
public class Etikett {

	@SuppressWarnings("unused")
	private static final long serialVersionUID = 6716611400533458082L;

	/**
	 * the full id as uri
	 */
	String uri = null;

	/**
	 * a label
	 */
	String label = null;
	/**
	 * a icon
	 */
	String icon = null;

	/**
	 * The name is a short-form for the uri used in JSON-LD
	 */
	String name = null;

	/**
	 * The expected type of the resource
	 */
	String referenceType = "class";

	/**
	 * Describes if the given is expected to occur as a \@set or a \@list. Can be
	 * null;
	 */
	public String container = null;

	/**
	 * The jaxb needs this
	 */
	public Etikett() {
		// for jaxb
	}

	/**
	 * @param subj is used as primary key in table
	 */
	public Etikett(String subj) {
		uri = subj;
	}

	/**
	 * @return the uri
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * @param uri the uri this etikett is about
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}

	/**
	 * @return a human readable label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * @param label a human readable label
	 */
	public void setLabel(String label) {
		this.label = label;
	}

	/**
	 * @return icon etikett icon
	 */
	public String getIcon() {
		return icon;
	}

	/**
	 * @param icon etikett icon
	 */
	public void setIcon(String icon) {
		this.icon = icon;
	}

	/**
	 * @return jsonName
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name The name is a short-form for the uri used in JSON-LD
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return default is class. If you are describing a predicate you should use
	 *         one of the types string,@id, date
	 */
	public String getReferenceType() {
		return referenceType;
	}

	/**
	 * @param referenceType The expected type of the resource
	 */
	public void setReferenceType(String referenceType) {
		this.referenceType = referenceType;
	}

	@Override
	public String toString() {
		try {
			return new ObjectMapper().writeValueAsString(this);
		} catch (Exception e) {
			return "To String failed " + e.getMessage();
		}
	}

}