/* Copyright 2018-2019 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.GeoDistanceQueryBuilder;
import org.elasticsearch.index.query.GeoPolygonQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import com.google.common.collect.ImmutableMap;

/**
 * Queries on the lobid-resources index.
 *
 * @author Fabian Steeg (fsteeg)
 */
public class Queries {
	/**
	 * Parameters for API requests.
	 *
	 * @author Fabian Steeg (fsteeg)
	 */
	static enum Parameter {
		ID(new Queries.IdQuery()), //
		NAME(new Queries.NameQuery()), //
		AGENT(new Queries.AuthorQuery()), //
		SUBJECT(new Queries.SubjectQuery()), //
		Q(new Queries.AllFieldsQuery()), //
		PUBLISHER(new Queries.PublisherQuery()), //
		ISSUED(new Queries.IssuedQuery()), //
		MEDIUM(new Queries.MediumQuery()), //
		LOCATION((new Queries.LocationQuery())), //
		SCROLL(new Queries.AllFieldsQuery()), //
		NESTED(new Queries.NestedQuery()), //
		T(new Queries.TypeQuery()), //
		FILTER(new Queries.FilterQuery()), //
		OWNER(new Queries.OwnerQuery()), //
		WORD(new Queries.WordQuery());

		private AbstractIndexQuery q;

		private Parameter(AbstractIndexQuery q) {
			this.q = q;
		}

		/**
		 * @return The parameter id (the string passed to the API)
		 */
		public String id() {
			return name().toLowerCase();
		}

		/**
		 * @return The query for this parameter
		 */
		public AbstractIndexQuery q() {
			return q;
		}

		public static Map<Parameter, String> select(
				ImmutableMap<Parameter, String> params) {
			Map<Parameter, String> selected = new HashMap<>();
			for (Map.Entry<Parameter, String> p : params.entrySet())
				if (isDefined(p.getValue()))
					selected.put(p.getKey(), p.getValue());
			return selected;
		}

		private static boolean isDefined(final String param) {
			return !param.isEmpty();
		}
	}

	private final String q;
	private final String agent;
	private final String name;
	private final String subject;
	private final String id;
	private final String publisher;
	private final String issued;
	private final String medium;
	private final String t;
	private final String owner;
	private final String nested;
	private final String location;
	private final String filter;
	private final String word;

	/**
	 * @param builder The builder to use for this
	 */
	public Queries(Builder builder) {
		this.q = builder.q;
		this.agent = builder.agent;
		this.name = builder.name;
		this.subject = builder.subject;
		this.id = builder.id;
		this.publisher = builder.publisher;
		this.issued = builder.issued;
		this.medium = builder.medium;
		this.t = builder.t;
		this.owner = builder.owner;
		this.nested = builder.nested;
		this.location = builder.location;
		this.filter = builder.filter;
		this.word = builder.word;
	}

	@SuppressWarnings("javadoc")
	public static class Builder {
		private String q = "";
		private String agent = "";
		private String name = "";
		private String subject = "";
		private String id = "";
		private String publisher = "";
		private String issued = "";
		private String medium = "";
		private String t = "";
		private String owner = "";
		private String nested = "";
		private String location = "";
		private String filter = "";
		private String word = "";

		//@formatter:off
		public Builder() {}
		public Builder q(String val) { q = val;	return this; }
		public Builder agent(String val) { agent = val; return this; }
		public Builder name(String val) { name = val; return this; }
		public Builder subject(String val) { subject = val; return this; }
		public Builder id(String val) { id = val; return this; }
		public Builder publisher(String val) { publisher = val; return this; }
		public Builder issued(String val) { issued = val; return this; }
		public Builder medium(String val) { medium = val; return this; }
		public Builder t(String val) { t = val; return this; }
		public Builder owner(String val) { owner = val; return this; }
		public Builder nested(String val) { nested = val; return this; }
		public Builder location(String val) { location = val; return this; }
		public Builder filter(String val) { filter = val; return this; }
		public Builder word(String val) { word = val; return this; }
		public QueryBuilder build() { return new Queries(this).query(); }
		//@formatter:on

		@Override
		public String toString() {
			final Map<Parameter, String> parameters = Parameter
					.select(new ImmutableMap.Builder<Parameter, String>() /*@formatter:off*/
							.put(Parameter.ID, id)
							.put(Parameter.Q, q)
							.put(Parameter.NAME, name)
							.put(Parameter.AGENT, agent)
							.put(Parameter.SUBJECT, subject)
							.put(Parameter.PUBLISHER, publisher)
							.put(Parameter.ISSUED, issued)
							.put(Parameter.MEDIUM, medium)
							.put(Parameter.NESTED, nested)
							.put(Parameter.LOCATION, location)
							.put(Parameter.T, t)
							.put(Parameter.FILTER, filter)
							.put(Parameter.OWNER, owner)
							.put(Parameter.WORD, word)
							.build());/*@formatter:on*/

			return parameters.toString();
		}

	}

	private QueryBuilder query() {
		final Map<Parameter, String> parameters = Parameter
				.select(new ImmutableMap.Builder<Parameter, String>() /*@formatter:off*/
						.put(Parameter.ID, id)
						.put(Parameter.Q, q)
						.put(Parameter.NAME, name)
						.put(Parameter.AGENT, agent)
						.put(Parameter.SUBJECT, subject)
						.put(Parameter.PUBLISHER, publisher)
						.put(Parameter.ISSUED, issued)
						.put(Parameter.MEDIUM, medium)
						.put(Parameter.NESTED, nested)
						.put(Parameter.LOCATION, location)
						.put(Parameter.T, t)
						.put(Parameter.FILTER, filter)
						.put(Parameter.OWNER, owner)
						.put(Parameter.WORD, word)
						.build());/*@formatter:on*/

		BoolQueryBuilder result = QueryBuilders.boolQuery();
		for (Entry<Parameter, String> p : parameters.entrySet()) {
			QueryBuilder queryBuilder = p.getKey().q.build(p.getValue());
			result = result.must(queryBuilder);
		}
		return result;
	}

	/**
	 * Superclass for queries on different indexes.
	 *
	 * @author Fabian Steeg (fsteeg)
	 */
	static abstract class AbstractIndexQuery {
		/**
		 * @return The index fields used by this query type
		 */
		public abstract List<String> fields();

		/**
		 * @param queryString The query string
		 * @return A query builder for this query type and the given query string
		 */
		public abstract QueryBuilder build(String queryString);

		/**
		 * @param queryString The query string
		 * @return The given query, without any boolean operator
		 */
		public static String withoutBooleanOperator(final String queryString) {
			return queryString.replaceAll(",AND|,OR", "");
		}

		/**
		 * @param queryString The query string
		 * @return True, if the given query is a boolean AND query
		 */
		public static boolean isAndQuery(String queryString) {
			return queryString.endsWith(",AND");
		}

		QueryBuilder multiValueMatchQuery(String queryString) {
			String queryValues = withoutBooleanOperator(queryString);
			boolean isAndQuery = isAndQuery(queryString);
			BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
			for (String q : queryValues.split(",")) {
				MatchQueryBuilder query = matchQuery(fields().get(0), q);
				boolQuery =
						isAndQuery ? boolQuery.must(query) : boolQuery.should(query);
			}
			return boolQuery;
		}

	}

	/**
	 * Query against all fields.
	 */
	public static class AllFieldsQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			final List<String> searchFields = new ArrayList<>(Arrays.asList("_all"));
			final List<String> suggestFields = new NameQuery().fields();
			searchFields.addAll(suggestFields);
			return searchFields;
		}

		@Override
		public QueryBuilder build(final String queryString) {
			QueryStringQueryBuilder query =
					QueryBuilders.queryStringQuery(queryString);
			for (String f : new NameQuery().fields()) {
				query = query.field(f, 1.0f);
			}
			return query.field(fields().get(0));
		}
	}

	/**
	 * Query the lobid-resources index using a resource name.
	 */
	public static class NameQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList("title", "otherTitleInformation");
		}

		@Override
		public QueryBuilder build(final String queryString) {
			String[] fields = fields().toArray(new String[] {});
			return multiMatchQuery(queryString, fields)
					.type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
					.operator(Operator.AND);
		}

	}

	/**
	 * Query the lobid-resources index using a resource author.
	 */
	public static class AuthorQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			return Arrays.asList("contribution.agent.id", "contribution.agent.label");
		}

		@Override
		public QueryBuilder build(final String queryString) {
			return searchAuthor(queryString);
		}

		QueryBuilder searchAuthor(final String search) {
			QueryBuilder query;
			final String lifeDates = "\\((\\d+)-(\\d*)\\)";
			final Matcher lifeDatesMatcher =
					Pattern.compile("[^(]+" + lifeDates).matcher(search);
			if (lifeDatesMatcher.find()) {
				query = createAuthorQuery(lifeDates, search, lifeDatesMatcher);
			} else if (isAndQuery(search)) {
				query = multiValueMatchQuery(search);
			} else if (search.matches("(https://d-nb\\.info/gnd/)?\\d+.*")) {
				final String term = search.startsWith("http") ? search
						: "https://d-nb.info/gnd/" + search;
				query = multiMatchQuery(term, fields().get(0));
			} else {
				query = nameMatchQuery(search);
			}
			return query;
		}

		private QueryBuilder createAuthorQuery(final String lifeDates,
				final String search, final Matcher matcher) {
			/* Search name in name field and birth in birth field: */
			final BoolQueryBuilder birthQuery = QueryBuilders.boolQuery()
					.must(nameMatchQuery(search.replaceAll(lifeDates, "").trim()))
					.must(matchQuery("contribution.agent.dateOfBirth", matcher.group(1)));
			return matcher.group(2).equals("") ? birthQuery :
			/* If we have one, search death in death field: */
					birthQuery.must(
							matchQuery("contribution.agent.dateOfDeath", matcher.group(2)));
		}

		private QueryBuilder nameMatchQuery(final String search) {
			return multiMatchQuery(search, fields().get(1)).operator(Operator.AND);
		}
	}

	/**
	 * Query the lobid-resources index using a resource subject.
	 */
	public static class SubjectQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			return Arrays.asList(); /* see build() below */
		}

		@Override
		public QueryBuilder build(final String queryString) {
			BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
			String queryValues = withoutBooleanOperator(queryString);
			boolean isAndQuery = isAndQuery(queryString);
			boolean hasLabel = hasLabel(queryValues);
			if (!queryString.contains("http") && Arrays.asList(queryString.split(","))
					.stream().filter(x -> x.trim().matches("[\\d\\-X]+")).count() == 0) {
				// no URI or GND-ID in queryString, ignore commas, e.g. "Ney, Elisabet":
				queryValues = queryString.replace(',', ' ');
			}
			for (String q : queryValues.split(",")) {
				String qTrimmed = q.trim();
				if (qTrimmed.startsWith("http") || qTrimmed.matches("[\\d\\-X]+")) {
					final String query = qTrimmed.startsWith("http") ? qTrimmed
							: "https://d-nb.info/gnd/" + qTrimmed;
					final MatchQueryBuilder subjectIdQuery =
							matchQuery(!query.contains("d-nb.info/gnd") ? "subject.id"
									: "subject.componentList.id", query.trim())
											.operator(Operator.AND);
					boolQuery = hasLabel || isAndQuery ? boolQuery.must(subjectIdQuery)
							: boolQuery.should(subjectIdQuery);
				} else {
					final MultiMatchQueryBuilder subjectLabelQuery =
							multiMatchQuery(qTrimmed,
									new String[] { "subject.componentList.label.unstemmed",
											"subjectAltLabel.unstemmed" }).operator(Operator.AND)
													.type(Type.CROSS_FIELDS);
					boolQuery = hasLabel || isAndQuery ? boolQuery.must(subjectLabelQuery)
							: boolQuery.should(subjectLabelQuery);
				}
			}
			return boolQuery;
		}

		private static boolean hasLabel(String queryString) {
			return Arrays.asList(queryString.split(",")).stream().filter(
					x -> !x.trim().startsWith("http") && !x.trim().matches("[\\d\\-X]+"))
					.count() > 0;
		}
	}

	/**
	 * Query the lobid-resources index using a resource ID.
	 */
	public static class IdQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			return Arrays.asList("isbn", "issn", "hbzId", "rpbId", "biblioVinoId");
		}

		@Override
		public QueryBuilder build(final String queryString) {
			return multiMatchQuery(normalizedLobidResourceIdQueryString(queryString),
					fields().toArray(new String[] {})).operator(Operator.AND);
		}

		private static String normalizedLobidResourceIdQueryString(
				final String queryString) {
			String normalizedQueryString = queryString.replaceAll(" ", "");
			if (!normalizedQueryString.contains("/")) // thus: doi or isbn
				if (normalizedQueryString.matches("\"?\\d.*\"?")) { // thus: isbn
					normalizedQueryString = normalizedQueryString.replaceAll("-", "");
				}
			return normalizedQueryString;
		}
	}

	/**
	 * Query the lobid-resources index for a given publisher.
	 */
	public static class PublisherQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList("publication.publishedBy");
		}

		@Override
		public QueryBuilder build(String queryString) {
			return multiMatchQuery(queryString, fields().toArray(new String[] {}))
					.operator(Operator.AND);
		}
	}

	/**
	 * Query the lobid-resources index for a given issued date.
	 */
	public static class IssuedQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList("publication.startDate");
		}

		@Override
		public QueryBuilder build(String queryString) {
			final String[] elems = queryString.split("-");
			if (elems.length == 2) {
				return QueryBuilders.rangeQuery(fields().get(0))//
						.gte(bound(elems[0])).lte(bound(elems[1]));
			}
			return multiMatchQuery(queryString, fields().toArray(new String[] {}));
		}

		private static String bound(final String val) {
			return val.equals("*") ? null /* = unbounded */ : val;
		}
	}

	/**
	 * Query the lobid-resources index for a given medium.
	 */
	public static class MediumQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList("medium.id");
		}

		@Override
		public QueryBuilder build(String queryString) {
			return multiValueMatchQuery(queryString);
		}

	}

	/**
	 * Type query TODO: are we not using a filter for this?
	 *
	 */
	public static class TypeQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList("type");
		}

		@Override
		public QueryBuilder build(String queryString) {
			return multiValueMatchQuery(queryString);
		}

	}

	/**
	 * A nested query.
	 *
	 */
	public static class NestedQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList();
		}

		@Override
		public QueryBuilder build(String nested) {
			String nestedFieldName = nested.substring(0, nested.indexOf(':'));
			String nestedQueryString = nested.substring(nested.indexOf(':') + 1);
			QueryBuilder nestedQuery = QueryBuilders.nestedQuery(nestedFieldName,
					QueryBuilders.queryStringQuery(nestedQueryString), ScoreMode.Avg);
			return nestedQuery;
		}

	}

	/**
	 * A filter query. TODO not actually used as filter here, is this working?
	 */
	public static class FilterQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList();
		}

		@Override
		public QueryBuilder build(String queryString) {
			return QueryBuilders.queryStringQuery(queryString);
		}
	}

	/**
	 * Query the lobid-resources set for results in a given point or polygon.
	 */

	public static class LocationQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList(Search.SPATIAL_GEO_FIELD);
		}

		@Override
		public QueryBuilder build(String queryString) {
			return polygonQuery(queryString);
		}

		private QueryBuilder polygonQuery(String location) {
			String[] points = location.split(" ");
			String field = fields().get(0);
			QueryBuilder result = null;
			if (points.length == 1) {
				result = geoDistanceFilter(field, locationArray(points[0]));
			} else if (points.length == 2) {
				result = QueryBuilders.boolQuery()
						.should(geoDistanceFilter(field, locationArray(points[0])))
						.should(geoDistanceFilter(field, locationArray(points[1])));
			} else {
				List<GeoPoint> geoPoints = new ArrayList<>();
				for (String point : points) {
					String[] latLon = locationArray(point);
					geoPoints.add(new GeoPoint(Double.parseDouble(latLon[0].trim()),
							Double.parseDouble(latLon[1].trim())));
				}
				GeoPolygonQueryBuilder filter =
						QueryBuilders.geoPolygonQuery(field, geoPoints);
				result = filter;
			}
			return result;
		}

		private static String[] locationArray(String loc) {
			String[] pointLocation = null;
			if (loc.contains(",")) {
				pointLocation = loc.split(",");
			} else {
				GeoPoint point = new GeoPoint(loc);
				pointLocation = new String[] { //
						String.valueOf(point.getLat()), String.valueOf(point.getLon()) };
			}
			return pointLocation;
		}

		private static GeoDistanceQueryBuilder geoDistanceFilter(String field,
				String[] latLon) {
			return QueryBuilders.geoDistanceQuery(field)
					.point(Double.parseDouble(latLon[0].trim()),
							Double.parseDouble(latLon[1].trim()))
					.distance("100m");
		}

	}

	/**
	 * Query the lobid-resources index using resource 'words'. This models a
	 * concept from the hbz union catalog, see
	 * https://github.com/hbz/nwbib/issues/110 for details.
	 */
	public static class WordQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			List<String> fields = new ArrayList<>();
			List<Parameter> exclude =
					Arrays.asList(Parameter.WORD, Parameter.LOCATION, Parameter.Q,
							Parameter.SCROLL, Parameter.SUBJECT, Parameter.ISSUED);
			for (Parameter p : Arrays.asList(Parameter.values()).stream()
					.filter(p -> !exclude.contains(p)).collect(Collectors.toList())) {
				fields.addAll(p.q.fields());
			}
			// no longer in SubjectQuery or unstemmed in SubjectQuery:
			fields.add("subject.label");
			fields.add("subject.componentList.label");
			fields.add("subjectAltLabel");
			fields.add("natureOfContent.label");
			return fields;
		}

		@Override
		public QueryBuilder build(String queryString) {
			QueryStringQueryBuilder builder =
					QueryBuilders.queryStringQuery(queryString);
			fields().stream().forEach(f -> builder.field(f));
			return builder.defaultOperator(Operator.AND);
		}
	}

	/**
	 * Query the lobid-resources index using resource 'words'. This models a
	 * concept from the hbz union catalog, see
	 * https://github.com/hbz/nwbib/issues/110 for details.
	 */
	public static class OwnerQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			List<String> fields = new ArrayList<>();
			return fields;
		}

		@Override
		public QueryBuilder build(String queryString) {
			final String prefix = Lobid.ORGS_ROOT;
			String suffix = "#!";
			BoolQueryBuilder ownersQuery = QueryBuilders.boolQuery();
			final String[] owners = queryString.split(",");
			for (String o : owners) {
				final String ownerId =
						prefix + o.replace(prefix, "").replace(suffix, "") + suffix;
				ownersQuery = ownersQuery
						.should(QueryBuilders.matchQuery(Search.OWNER_ID_FIELD, ownerId));
			}
			return ownersQuery;
		}
	}

}
