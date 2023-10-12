/* Copyright 2014-2017 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package views;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.html.HtmlEscapers;

import controllers.resources.Lobid;
import play.Logger;

/**
 * Different ways of serializing a table row
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
/* no javadoc for elements. */
public enum TableRow {

	VALUES {
		@Override
		public String process(JsonNode doc, String property, String param,
				String label, List<String> values, Optional<List<String>> keys) {
			List<String> vs = values;
			return vs.isEmpty() ? ""
					: String.format("<tr><td>%s</td><td>%s</td></tr>", label,
							vs.stream().map(val -> label(doc, property, param, val, keys))
									.collect(Collectors.joining(
											property.equals("subjectChain") ? " <br/> " : " | ")));
		}

		private String label(JsonNode doc, String property, String param,
				String val, Optional<List<String>> labels) {
			String value = property.equals("subjectChain")
					? val.replaceAll("\\([\\d,]+\\)$", "").trim() : val;
			if (!labels.isPresent()) {
				return refAndLabel(property, value, labels)[0];
			}
			String term = value;
			if (param.equals("q")) {
				term = "\"" + value + "\"";
			}
			try {
				term = URLEncoder.encode(term, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				Logger.error("Could not call encode '{}'", term, e);
			}
			String search = String.format("/resources/search?%s=%s", param, term);
			JsonNode node = doc.get(property);
			String label = labelForId(value, node, labels);
			String result = labels.get().contains("numbering") ? label
					: String.format(
							"<a title=\"Nach weiteren Titeln suchen\" href=\"%s\">%s</a>",
							search, label);
			if (value.startsWith("http")) {
				if (param.equals("agent") || param.equals("subject")
						&& !value.contains("http://dewey.info")) {
					result += String.format(
							" <a title=\"Linked-Data-Quelle abrufen\" "
									+ "href=\"%s\"><span class=\"glyphicon glyphicon-link\"></span></a>",
							value);
				}
			}
			return result;
		}
	},
	VALUES_MULTI {
		@Override
		public String process(JsonNode doc, String property, String param,
				String label, List<String> values, Optional<List<String>> keys) {
			if (!keys.isPresent()) {
				throw new IllegalArgumentException("VALUES_MULTI needs valueLabels");
			}
			JsonNode node = doc.get(property).iterator().next();
			return values
					.stream().map(val -> String.format("<tr><td>%s</td><td>%s</td></tr>",
							label, label(node, val, keys.get())))
					.collect(Collectors.joining("\n"));
		}

		private String label(JsonNode doc, String value, List<String> properties) {
			List<String> results = new ArrayList<>();
			List<String> resultValues = labelsFor(doc, value, properties);
			for (int i = 0; i < resultValues.size(); i++) {
				String currentValue = resultValues.get(i);
				String[] refAndLabel =
						refAndLabel(properties.get(i), currentValue, Optional.empty());
				String result =
						properties.get(i).equals("numbering") || value.equals("--")
								? currentValue
								: String.format(
										"<a title=\"Titeldetails anzeigen\" href=\"%s\">%s</a>",
										refAndLabel[0], refAndLabel[1]);
				results.add(result.replaceAll("^(Band|Volume)", "").trim());
			}
			return results.stream().collect(Collectors.joining(String.format(", %s",
					results.toString().contains("Band") ? "" : "Band ")));
		}

		private List<String> labelsFor(JsonNode doc, String value,
				List<String> keys) {
			List<String> result = new ArrayList<>();
			if (doc != null && doc.get(keys.get(0)) != null) {
				JsonNode node = doc.get(keys.get(0)).iterator().next();
				JsonNode id = node.get("id");
				JsonNode label = node.get("label");
				result.add(id != null ? id.textValue()
						: label != null ? label.textValue() : "--");
				JsonNode val = doc.get(keys.get(1));
				if (val != null)
					result.add(val.textValue());
			}
			return result.isEmpty() ? Arrays.asList(value) : result;
		}
	},
	LINKS {
		@Override
		public String process(JsonNode doc, String property, String param,
				String label, List<String> values, Optional<List<String>> labels) {
			return values.stream()
					.filter(value -> !value.contains("lobid.org/resource/NWBib"))
					.map(value -> String.format("<tr><td>%s</td><td>%s</td></tr>", label,
							link(property, value, labels)))
					.collect(Collectors.joining("\n"));
		}

		private String link(String property, String val,
				Optional<List<String>> labels) {
			String[] refAndLabel = refAndLabel(property, val, labels);
			String url = refAndLabel[0];
			String label = refAndLabel[1];
			String resources = "/resources";
			return String.format("<a title='%s' href='%s'>%s</a>", url,
					url.contains(resources) ? url.substring(url.indexOf(resources)) : url,
					label);
		}

	};

	/**
	 * @param id The ID
	 * @param doc The full document
	 * @param labelKeys Keys of the values to try as labels for the ID
	 * @return An HTML-escaped label for the ID
	 */
	public static String labelForId(String id, JsonNode doc,
			Optional<List<String>> labelKeys) {
		String label = graphObjectLabelForId(id, doc, labelKeys);
		return HtmlEscapers.htmlEscaper().escape(label);
	}

	private static String graphObjectLabelForId(String id, JsonNode doc,
			Optional<List<String>> labelKeys) {
		if (!labelKeys.isPresent() || labelKeys.get().isEmpty() || doc == null) {
			return id;
		}

		List<JsonNode> graphs = doc.findValues("@graph");
		for (JsonNode node : graphs.isEmpty() ? doc : graphs.get(0)) {
			for (String key : labelKeys.get()) {
				String idField = node.has("id") ? "id" : "@id";
				if (node.has(key) && node.has(idField)
						&& node.get(idField).textValue().equals(id)) {
					JsonNode label = node.get(key);
					if (label != null && label.isTextual()
							&& !label.textValue().trim().isEmpty()) {
						return label.textValue() + lifeDates(node);
					}
				}
			}
		}
		return id;
	}

	private static String lifeDates(JsonNode node) {
		JsonNode birth = node.get("dateOfBirth");
		JsonNode death = node.get("dateOfDeath");
		if (birth != null) {
			return String.format(" (%s-%s)", birth.textValue(),
					death != null ? death.textValue() : "");
		}
		return "";
	}

	String[] refAndLabel(String property, String value,
			Optional<List<String>> labels) {
		if ((property.equals("containedIn") || property.equals("hasPart")
				|| property.equals("isPartOf") || property.equals("hasSuperordinate"))
				&& value.contains("lobid.org")) {
			return new String[] {
					value.replaceAll("lobid.org/resource/", "lobid.org/resources/"),
					Lobid.resourceLabel(value) };
		}
		String label;
		try {
			label =
					labels.isPresent() && labels.get().size() > 0 ? labels.get().get(0)
							: value.startsWith("http") ? new URI(value).getHost() : value;
		} catch (URISyntaxException e) {
			Logger.warn("No valid URI: {}", value);
			label = value;
		}
		return new String[] { value, label };
	}

	public abstract String process(JsonNode doc, String property, String param,
			String label, List<String> values, Optional<List<String>> labels);
}