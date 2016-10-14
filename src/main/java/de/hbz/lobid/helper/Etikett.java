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

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This class is used in ApplicationProfile
 * 
 * @author Jan Schnasse
 *
 */
@XmlRootElement
public class Etikett {
	final static Logger logger = LoggerFactory.getLogger(Etikett.class);

	public enum EtikettType {
		CACHE, CONTEXT, STORE
	}

	/**
	 * @param subj is used as primary key in table
	 */
	public Etikett(String subj) {
		uri = subj;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 6716611400533458082L;

	/**
	 * the full id as uri
	 */
	public String uri = null;

	public String comment = null;

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
	public String referenceType = null;

	/**
	 * Describes if the given is expected to occur as a \@set or a \@list. Can be
	 * null;
	 */
	public String container = null;

	/**
	 * A weigth for ordering
	 */
	public String weight = null;

	public EtikettType type;

	public Map<String, String> multilangLabel = new HashMap<>();
	@JsonIgnore
	public String multiLangSerialized;

	/**
	 * @param e attributes from e will be copied to this etikett
	 */
	public void copy(Etikett e) {
		icon = e.icon;
		label = e.label;
		name = e.name;
		referenceType = e.referenceType;
		container = e.container;
		comment = e.comment;
		weight = e.weight;
		type = e.type;
		multiLangSerialized = e.multiLangSerialized;
	}

	public Etikett() {
		// needed for jaxb (@see https://github.com/hbz/lobid-rdf-to-json
	}

	public String toString() {
		try {
			return new ObjectMapper().writeValueAsString(this);
		} catch (Exception e) {
			return "To String failed " + e.getMessage();
		}
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
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

	public String getContainer() {
		return container;
	}

	public void setContainer(String container) {
		this.container = container;
	}

	public String getWeight() {
		return weight;
	}

	public void setWeight(String weight) {
		this.weight = weight;
	}

	public EtikettType getType() {
		return type;
	}

	public void setType(EtikettType type) {
		this.type = type;
	}

	@JsonIgnore
	public String getMultiLangSerialized() {
		return multiLangSerialized;
	}

	public void setMultiLangSerialized(String multiLangSerialized) {
		this.multiLangSerialized = multiLangSerialized;
		try {
			multilangLabel = new ObjectMapper().readValue(
					new ByteArrayInputStream(multiLangSerialized.getBytes()),
					new TypeReference<HashMap<String, String>>() {
					});
		} catch (Exception e) {
			logger.warn("", e);
		}
	}

	public void addMultilangLabel(String lang, String label) {
		multilangLabel.put(lang, label);
		setMultilangLabel(multilangLabel);
	}

	public void setMultilangLabel(Map<String, String> multilangLabel) {
		this.multilangLabel = multilangLabel;
		try {
			multiLangSerialized =
					new ObjectMapper().writeValueAsString(multilangLabel);
		} catch (JsonProcessingException e) {
			logger.warn("", e);
		}
	}

	public Map<String, String> getMultilangLabel() {
		return multilangLabel;
	}

}