/* Copyright 2014-2015 Fabian Steeg, hbz. Licensed under the GPLv2 */

package views;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.html.HtmlEscapers;

import controllers.nwbib.Classification;
import controllers.nwbib.Lobid;

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
			return values.isEmpty() ? ""
					: String.format("<tr><td>%s</td><td>%s</td></tr>", label,
							values.stream()
									.filter(value -> !value.contains("http://dewey.info"))
									.map(val -> label(doc, property, param, val, keys))
									.collect(Collectors.joining(" | ")));
		}

		private String label(JsonNode doc, String property, String param,
				String val, Optional<List<String>> labels) {
			String value = property.equals("subjectChain")
					? val.replaceAll("\\([\\d,]+\\)$", "").trim() : val;
			if (!labels.isPresent()) {
				return refAndLabel(property, value, labels)[0];
			}
			String term = param.equals("q") ? "\"" + value + "\"" : value;

			try {
				term = URLEncoder.encode(term, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			String search = String.format("%s/search?%s=%s",
					controllers.nwbib.routes.Application.index(), param, term);
			String label = labelForId(value, doc, labels);
			String result = labels.get().contains("numbering") ? label
					: String.format(
							"<a title=\"Nach weiteren Titeln suchen\" href=\"%s\">%s</a>",
							search, label);
			if (value.startsWith("http")) {
				result += String.format(
						" <a title=\"Linked-Data-Quelle abrufen\" "
								+ "href=\"%s\"><span class=\"glyphicon glyphicon-link\"></span></a>",
						value);
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
			return values.stream()
					.filter(value -> !value.contains("http://dewey.info"))
					.map(val -> String.format("<tr><td>%s</td><td>%s</td></tr>", label,
							label(doc, val, keys.get())))
					.collect(Collectors.joining("\n"));
		}

		private String label(JsonNode doc, String value, List<String> properties) {
			List<String> results = new ArrayList<>();
			List<String> resultValues = labelsFor(doc, value, properties);
			for (int i = 0; i < resultValues.size(); i++) {
				String currentValue = resultValues.get(i);
				String[] refAndLabel =
						refAndLabel(properties.get(i), currentValue, Optional.empty());
				String result = properties.get(i).equals("numbering") ? currentValue
						: String.format(
								"<a title=\"Nach weiteren Titeln suchen\" href=\"%s\">%s</a>",
								refAndLabel[0], refAndLabel[1]);
				results.add(result);
			}
			return results.stream().collect(Collectors.joining(", Band "));
		}

		private List<String> labelsFor(JsonNode doc, String value,
				List<String> keys) {
			List<String> result = new ArrayList<>();
			for (JsonNode node : doc.findValues("@graph").get(0)) {
				for (String key : keys) {
					if (node.get("@id").textValue().equals(value) && node.has(key)) {
						result.add(node.get(key).textValue());
					}
				}
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
			String href = refAndLabel[0];
			String label = refAndLabel[1];
			return String.format("<a title='%s' href='%s'>%s</a>", href, href, label);
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
		String label = "";
		if (id.startsWith("http://purl.org/lobid/nwbib")) {
			label = String.format("%s (%s)", //
					Lobid.facetLabel(Arrays.asList(id), null, null),
					Classification.shortId(id));
		} else {
			label = graphObjectLabelForId(id, doc, labelKeys);
		}
		return HtmlEscapers.htmlEscaper().escape(label);
	}

	private static String graphObjectLabelForId(String id, JsonNode doc,
			Optional<List<String>> labelKeys) {
		if (!labelKeys.isPresent() || labelKeys.get().isEmpty()) {
			return id;
		}
		for (JsonNode node : doc.findValues("@graph").get(0)) {
			for (String key : labelKeys.get()) {
				if (node.get("@id").textValue().equals(id) && node.has(key)) {
					JsonNode label = node.get(key);
					if (label != null && label.isTextual()
							&& !label.textValue().trim().isEmpty()) {
						return label.textValue();
					}
				}
			}
		}
		return id;
	}

	String[] refAndLabel(String property, String value,
			Optional<List<String>> labels) {
		if ((property.equals("containedIn") || property.equals("hasPart")
				|| property.equals("isPartOf") || property.equals("multiVolumeWork")
				|| property.equals("series")) && value.contains("lobid.org")) {
			return new String[] {
					value.replace("lobid.org/resource/", "lobid.org/nwbib/"),
					Lobid.resourceLabel(value) };
		}
		String label =
				labels.isPresent() && labels.get().size() > 0 ? labels.get().get(0)
						: value.startsWith("http") ? URI.create(value).getHost() : value;
		return new String[] { value, label };
	}

	public abstract String process(JsonNode doc, String property, String param,
			String label, List<String> values, Optional<List<String>> labels);
}