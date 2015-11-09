/* Copyright 2014-2015 Fabian Steeg, hbz. Licensed under the GPLv2 */

package views;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

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
			return values.stream()
					.filter(value -> !value.contains("http://dewey.info"))
					.map(val -> String.format("<tr><td>%s</td><td>%s</td></tr>", label,
							label(doc, property, param, val, keys)))
					.collect(Collectors.joining("\n"));
		}

		private String label(JsonNode doc, String property, String param,
				String value, Optional<List<String>> labels) {
			if (!labels.isPresent()) {
				return refAndLabel(property, value)[0];
			}
			String term = param.equals("q") ? "\"" + value + "\"" : value;

			try {
				term = URLEncoder.encode(term, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			String search = String.format("%s/search?%s=%s",
					controllers.nwbib.routes.Application.index(), param, term);
			String label = createLabel(doc, value, labels);
			String result = labels.get().contains("numbering") ? label
					: String.format(
							"<a title=\"Nach weiteren Titeln suchen\" href=\"%s\">%s</a>",
							search, label);
			if (value.startsWith("http")) {
				result += String.format(
						" | <a title=\"Linked-Data-Quelle abrufen\" "
								+ "href=\"%s\"><span class=\"glyphicon glyphicon-link\"></span></a>",
						value);
			}
			return result;
		}

		private String createLabel(JsonNode doc, String value,
				Optional<List<String>> labels) {
			String label = "";
			if (value.startsWith("http://purl.org/lobid/nwbib")) {
				label = String.format("%s (%s)", //
						Lobid.facetLabel(Arrays.asList(value), null, null),
						Classification.shortId(value));
			} else {
				label = labelFor(doc, value, labels);
			}
			return label;
		}

		private String labelFor(JsonNode doc, String value,
				Optional<List<String>> keys) {
			if (!keys.isPresent() || keys.get().isEmpty()) {
				return value;
			}
			List<String> result = new ArrayList<>();
			for (JsonNode node : doc.get("@graph")) {
				for (String key : keys.get()) {
					if (node.get("@id").textValue().equals(value) && node.has(key)) {
						result.add(node.get(key).textValue());
					}
				}
			}
			return result.isEmpty() ? value : result.get(0);
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
				String[] refAndLabel = refAndLabel(properties.get(i), currentValue);
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
			for (JsonNode node : doc.get("@graph")) {
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
							link(property, value)))
					.collect(Collectors.joining("\n"));
		}

		private String link(String property, String val) {
			String[] refAndLabel = refAndLabel(property, val);
			String href = refAndLabel[0];
			String label = refAndLabel[1];
			return String.format("<a title='%s' href='%s'>%s</a>", href, href, label);
		}

	};

	String[] refAndLabel(String property, String value) {
		if (property.equals("subjectChain")) {
			return new String[] { value.replaceAll("\\([\\d,]+\\)$", ""), "" };
		} else if ((property.equals("containedIn") || property.equals("hasPart")
				|| property.equals("isPartOf") || property.equals("multiVolumeWork")
				|| property.equals("series")) && value.contains("lobid.org")) {
			return new String[] {
					value.replace("lobid.org/resource/", "lobid.org/nwbib/"),
					Lobid.resourceLabel(value) };
		}
		return new String[] { value, value };
	}

	public abstract String process(JsonNode doc, String property, String param,
			String label, List<String> values, Optional<List<String>> labels);
}