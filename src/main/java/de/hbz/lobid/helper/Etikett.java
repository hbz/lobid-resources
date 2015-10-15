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
 * This class is used in ApplicationProfile
 * 
 * @author Jan Schnasse
 *
 */
@XmlRootElement
public class Etikett {

    /**
     * 
     */
    private static final long serialVersionUID = 6716611400533458082L;

    /**
     * the full id as uri
     */
    public String uri = null;

    /**
     * a label
     */
    public String label = null;
    /**
     * a icon
     */
    public String icon = null;

    /**
     * The name is a short-form for the uri used in JSON-LD
     */
    public String name = null;

    /**
     * The expected type of the resource
     */
    public String referenceType = "class";

    public Etikett() {
	// for jaxb
    }

    /**
     * @param subj
     *            is used as primary key in table
     */
    public Etikett(String subj) {
	uri = subj;
    }

    public String toString() {
	try {
	    return new ObjectMapper().writeValueAsString(this);
	} catch (Exception e) {
	    return "To String failed " + e.getMessage();
	}
    }

    /**
     * @param e
     *            attrbutes from e will be copied to this etikett
     */
    public void copy(Etikett e) {
	icon = e.icon;
	label = e.label;
	name = e.name;
    }

    public String getUri() {
	return uri;
    }

    public void setUri(String uri) {
	this.uri = uri;
    }

    public String getLabel() {
	return label;
    }

    public void setLabel(String label) {
	this.label = label;
    }

    public String getIcon() {
	return icon;
    }

    public void setIcon(String icon) {
	this.icon = icon;
    }

    public String getName() {
	return name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getReferenceType() {
	return referenceType;
    }

    public void setReferenceType(String referenceType) {
	this.referenceType = referenceType;
    }

}