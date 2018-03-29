/* Copyright 2014-2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package controllers.resources;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Spliterators;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.join.aggregations.Children;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.geogrid.GeoHashGrid;
import org.elasticsearch.search.aggregations.bucket.geogrid.InternalGeoHashGrid;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.gdata.util.common.base.PercentEscaper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import controllers.resources.Accept.Format;
import controllers.resources.RdfConverter.RdfFormat;
import play.Logger;
import play.Play;
import play.cache.Cache;
import play.cache.Cached;
import play.data.Form;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import play.twirl.api.Html;
import views.html.api;
import views.html.dataset;
import views.html.details;
import views.html.details_item;
import views.html.index;
import views.html.query;
import views.html.stars;

/**
 * The main application controller.
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class Application extends Controller {

	static final int MAX_FACETS = 150;

	private static final String STARRED = "starred";

	/** The internal ES field for the type facet. */
	public static final String TYPE_FIELD = "type";
	/** The internal ES field for the medium facet. */
	public static final String MEDIUM_FIELD = "medium.id";
	/** The internal ES aggregation name for the owner facet. */
	public static final String OWNER_AGGREGATION = "owner";
	/** The internal ES field for the topics. */
	public static final String TOPIC_AGGREGATION = "subject.label.raw";
	/** The internal ES field for subjects. */
	public static final String SUBJECT_FIELD = "subject.componentList.id";
	/** The internal ES field for contributing agents. */
	public static final String AGENT_FIELD = "contribution.agent.id";
	/** The internal ES field for issued years. */
	public static final String ISSUED_FIELD = "publication.startDate";
	/** Access to the resources.conf config file. */
	public final static Config CONFIG =
			ConfigFactory.parseFile(new File("conf/resources.conf")).resolve();

	static Form<String> queryForm = Form.form(String.class);

	static final int ONE_HOUR = 60 * 60;
	/** The number of seconds in one day. */
	public static final int ONE_DAY = 24 * ONE_HOUR;

	/**
	 * @return The index page.
	 */
	@Cached(key = "index", duration = ONE_DAY)
	public static Promise<Result> index() {
		return Promise.promise(() -> {
			JsonNode dataset = Json.parse(readFile("dataset"));
			final Form<String> form = queryForm.bindFromRequest();
			if (form.hasErrors())
				return badRequest(index.render(dataset));
			return ok(index.render(dataset));
		});
	}

	/**
	 * @return The API documentation page
	 */
	public static Promise<Result> api() {
		return Promise.promise(() -> ok(api.render()));
	}

	/**
	 * @return The advanced search page.
	 */
	@Cached(key = "search.advanced", duration = ONE_HOUR)
	public static Promise<Result> advanced() {
		return Promise.promise(() -> ok(views.html.advanced.render()));
	}

	/**
	 * @return The current full URI, URL-encoded, or null.
	 */
	public static String currentUri() {
		try {
			return URLEncoder.encode(request().host() + request().uri(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			Logger.error("Could not get current URI", e);
		}
		return null;
	}

	/**
	 * @param q Query to search in all fields
	 * @param agent Query for a agent associated with the resource
	 * @param name Query for the resource name (title)
	 * @param subject Query for the resource subject
	 * @param id Query for the resource id
	 * @param publisher Query for the resource publisher
	 * @param issued Query for the resource issued year
	 * @param medium Query for the resource medium
	 * @param from The page start (offset of page of resource to return)
	 * @param size The page size (size of page of resource to return)
	 * @param owner Owner filter for resource queries
	 * @param t Type filter for resource queries
	 * @param sort Sorting order for results ("newest", "oldest", "" -> relevance)
	 * @param word The 'word' query, a concept from the hbz union catalog
	 * @param format The response format (see {@code Accept.Format})
	 * @param aggs The comma separated aggregation fields
	 * @param location A single "lat,lon" point or space delimited points polygon
	 * @param nested A nested query, formatted as "<nested field>:<query string>"
	 * @param filter A filter to apply to the query, supports query string syntax
	 * @return The search results
	 */
	public static Promise<Result> query(final String q, final String agent,
			final String name, final String subject, final String id,
			final String publisher, final String issued, final String medium,
			final int from, final int size, final String owner, String t, String sort,
			String word, String format, String aggs, String location, String nested,
			String filter) {
		final String aggregations = aggs == null ? "" : aggs;
		if (!aggregations.isEmpty() && !Search.SUPPORTED_AGGREGATIONS
				.containsAll(Arrays.asList(aggregations.split(",")))) {
			return Promise.promise(() -> badRequest(
					String.format("Unsupported aggregations: %s (supported: %s)",
							aggregations, Search.SUPPORTED_AGGREGATIONS)));
		}
		addCorsHeader();
		String uuid = session("uuid");
		if (uuid == null) {
			uuid = UUID.randomUUID().toString();
			session("uuid", uuid);
		}
		String responseFormat = Accept.formatFor(format, request().acceptedTypes());
		boolean isBulkRequest =
				responseFormat.equals(Accept.Format.BULK.queryParamString);
		if (isBulkRequest) {
			response().setHeader("Content-Disposition",
					String.format(
							"attachment; filename=\"lobid-resources-bulk-%s.jsonl\"",
							System.currentTimeMillis()));
		}
		String cacheId = String.format("%s-%s-%s-%s", uuid, request().uri(),
				Accept.formatFor(format, request().acceptedTypes()), starredIds());
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		Logger.debug("Not cached: {}, will cache for one hour", cacheId);
		QueryBuilder queryBuilder = new Queries.Builder().q(q).agent(agent)
				.name(name).subject(subject).id(id).publisher(publisher).issued(issued)
				.medium(medium).t(t).owner(owner).nested(nested).location(location)
				.filter(filter).word(word).build();
		Search index = new Search.Builder().query(queryBuilder).from(from)
				.size(size).sort(sort).aggs(aggregations).build();
		Promise<Result> result;
		if (isBulkRequest) {
			result = bulkResult(q, nested, owner, index);
		} else {
			result = Promise.promise(() -> {
				Search queryResources = index.queryResources();
				boolean returnSuggestions = responseFormat.startsWith("json:");
				JsonNode json = returnSuggestions
						? toSuggestions(queryResources.getResult(), format.split(":")[1])
						: queryResources.getResult();
				String s = json.toString();
				boolean htmlRequested =
						responseFormat.equals(Accept.Format.HTML.queryParamString);
				return htmlRequested
						? ok(query.render(s, q, agent, name, subject, id, publisher, issued,
								medium, from, size, queryResources.getTotal(), owner, t, sort,
								word))
						: (returnSuggestions ? withCallback(json)
								: responseFor(withQueryMetadata(json, index),
										Accept.Format.JSON_LD.queryParamString));
			});
		}
		cacheOnRedeem(cacheId, result, ONE_HOUR);
		return result.recover((Throwable throwable) -> {
			Html html = query.render("[]", q, agent, name, subject, id, publisher,
					issued, medium, from, size, 0L, owner, t, sort, word);
			String message = "Could not query index: " + throwable.getMessage();
			boolean badRequest = throwable instanceof IllegalArgumentException;
			if (badRequest) {
				Logger.warn(message, throwable);
				flashError(badRequest);
				return badRequest(html);
			}
			Logger.error(message, throwable);
			return internalServerError(html);
		});
	}

	private static Promise<Result> bulkResult(final String q, final String nested,
			final String owner, Search index) {
		return Promise.promise(() -> {
			Chunks<String> chunks = StringChunks.whenReady(out -> {
				SearchResponse lastResponse =
						index.<SearchResponse> withClient((Client client) -> {
							QueryBuilder query = new Queries.Builder().q(q).nested(nested)
									.owner(owner).build();
							Search.validate(client, query);
							Logger.trace("bulkResources: q={}, owner={}, query={}", q, owner,
									query);
							TimeValue keepAlive = new TimeValue(60000);
							SearchResponse scrollResp =
									client.prepareSearch(Search.INDEX_NAME)
											.addSort(FieldSortBuilder.DOC_FIELD_NAME, SortOrder.ASC)
											.setScroll(keepAlive).setQuery(query)
											.setSize(100 /* hits per shard for each scroll */).get();
							String scrollId = scrollResp.getScrollId();
							while (scrollResp.getHits().iterator().hasNext()) {
								scrollResp.getHits().forEach((hit) -> {
									out.write(hit.getSourceAsString());
									out.write("\n");
								});
								scrollResp = client.prepareSearchScroll(scrollId)
										.setScroll(keepAlive).execute().actionGet();
								scrollId = scrollResp.getScrollId();
							}
							out.close();
							return scrollResp;
						});
				Logger.trace("Last search response for bulk request: " + lastResponse);
			});
			return ok(chunks).as(Accept.Format.BULK.types[0]);
		});
	}

	private static JsonNode withQueryMetadata(JsonNode json, Search index) {
		ObjectNode result = Json.newObject();
		String host = "http://" + request().host();
		result.put("@context", host + routes.Application.context());
		result.put("id", host + request().uri());
		result.put("totalItems", index.getTotal());
		result.putPOJO("member", json);
		if (index.getAggregations() != null) {
			result.putPOJO("aggregation", aggregationsAsJson(index));
		}
		return result;
	}

	private static Status withCallback(final JsonNode json) {
		/* JSONP callback support for remote server calls with JavaScript: */
		final String[] callback =
				request() == null || request().queryString() == null ? null
						: request().queryString().get("callback");
		return callback != null ? ok(String.format("/**/%s(%s)", callback[0], json))
				.as("application/javascript; charset=utf-8") : ok(json);
	}

	private static JsonNode toSuggestions(JsonNode json, String field) {
		Stream<JsonNode> documents = StreamSupport
				.stream(Spliterators.spliteratorUnknownSize(json.elements(), 0), false);
		Stream<JsonNode> suggestions = documents.flatMap((JsonNode document) -> {
			Stream<JsonNode> nodes = fieldValues(field, document);
			return nodes.map((JsonNode node) -> {
				boolean isTextual = node.isTextual();
				Optional<JsonNode> label = isTextual ? Optional.ofNullable(node)
						: findValueOptional(node, "label");
				Optional<JsonNode> id = isTextual ? getOptional(document, "id")
						: findValueOptional(node, "id");
				Optional<JsonNode> type = isTextual ? getOptional(document, "type")
						: findValueOptional(node, "type");
				JsonNode types = type.orElseGet(() -> Json.toJson(new String[] { "" }));
				String typeText = types.elements().next().textValue();
				return Json.toJson(ImmutableMap.of(//
						"label", label.orElseGet(() -> Json.toJson("")), //
						"id", id.orElseGet(() -> label.orElseGet(() -> Json.toJson(""))), //
						"category",
						typeText.equals("BibliographicResource")
								? Lobid.typeLabel(Json.fromJson(types, List.class))
								: typeText));
			});
		});
		return Json.toJson(suggestions.collect(Collectors.toSet()));
	}

	private static Stream<JsonNode> fieldValues(String field, JsonNode document) {
		return document.findValues(field).stream().flatMap((node) -> {
			return node.isArray()
					? StreamSupport.stream(
							Spliterators.spliteratorUnknownSize(node.elements(), 0), false)
					: Arrays.asList(node).stream();
		});
	}

	private static Optional<JsonNode> findValueOptional(JsonNode json,
			String field) {
		return Optional.ofNullable(json.findValue(field));
	}

	private static Optional<JsonNode> getOptional(JsonNode json, String field) {
		return Optional.ofNullable(json.get(field));
	}

	private static JsonNode aggregationsAsJson(Search index) {
		ObjectNode aggregations = Json.newObject();
		for (final Entry<String, Aggregation> aggregation : index.getAggregations()
				.asMap().entrySet()) {
			Aggregation value = aggregation.getValue();
			Stream<Map<String, Object>> buckets = collectAggregation(value);
			aggregations.putPOJO(aggregation.getKey(),
					Json.toJson(buckets.collect(Collectors.toList())));
		}
		return aggregations;
	}

	private static Stream<Map<String, Object>> collectAggregation(
			Aggregation value) {
		Stream<Map<String, Object>> buckets;
		if (value instanceof InternalGeoHashGrid) {
			GeoHashGrid grid = (GeoHashGrid) value;
			buckets = grid.getBuckets().stream().map((GeoHashGrid.Bucket b) -> {
				GeoPoint point = new GeoPoint(b.getKeyAsString());
				String latLon = point.lat() + "," + point.lon();
				return ImmutableMap.of("key", latLon, "doc_count", b.getDocCount());
			});
		} else {
			Terms terms = (Terms) (value instanceof Children
					? ((Children) value).getAggregations().get(OWNER_AGGREGATION)
					: value);
			buckets = terms.getBuckets().stream().map((Terms.Bucket b) -> ImmutableMap
					.of("key", b.getKeyAsString(), "doc_count", b.getDocCount()));
		}
		return buckets;
	}

	/**
	 * @param id The resource ID.
	 * @param format The response format (see {@code Accept.Format})
	 * @return The details page for the resource with the given ID.
	 */
	public static Promise<Result> resourceDotFormat(final String id,
			String format) {
		return resource(id, format);
	}

	/**
	 * @param id The resource ID.
	 * @param format The response format (see {@code Accept.Format})
	 * @return The details page for the resource with the given ID.
	 */
	public static Promise<Result> resource(final String id, String format) {
		addCorsHeader();
		String responseFormat = Accept.formatFor(format, request().acceptedTypes());
		String cacheId = String.format("show(%s,%s)", id, responseFormat);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		Promise<Result> promise = Promise.promise(() -> {
			JsonNode result =
					new Search.Builder().build().getResource(id).getResult();
			boolean htmlRequested =
					responseFormat.equals(Accept.Format.HTML.queryParamString);
			if (htmlRequested) {
				return result != null
						? ok(details.render(CONFIG, result.toString(), id))
						: notFound(details.render(CONFIG, "", id));
			}
			return result != null ? responseFor(result, responseFormat)
					: notFound("\"Not found: " + id + "\"");
		});
		cacheOnRedeem(cacheId, promise, ONE_DAY);
		return promise;
	}

	/**
	 * @param id The resource ID.
	 * @param format The response format (see {@code Accept.Format})
	 * @return The details page for the resource with the given ID.
	 */
	public static Promise<Result> itemDotFormat(final String id, String format) {
		return item(id, format);
	}

	/**
	 * @param id The item ID.
	 * @param format The response format (see {@code Accept.Format})
	 * @return The details for the item with the given ID.
	 */
	public static Promise<Result> item(final String id, String format) {
		String responseFormat = Accept.formatFor(format, request().acceptedTypes());
		String cacheId = String.format("item(%s,%s)", id, responseFormat);
		@SuppressWarnings("unchecked")
		Promise<Result> cachedResult = (Promise<Result>) Cache.get(cacheId);
		if (cachedResult != null)
			return cachedResult;
		Promise<Result> promise = Promise.promise(() -> {
			/* @formatter:off
			 * Escape item IDs for index lookup the same way as during transformation, see:
			 * https://github.com/hbz/lobid-resources/blob/master/src/main/resources/morph-hbz01-to-lobid.xml#L781
			 * https://github.com/hbz/lobid-resources/blob/master/src/main/java/org/lobid/resources/UrlEscaper.java#L31
			 * @formatter:on
			 */
			JsonNode itemJson = new Search.Builder().build()
					.getItem(
							new PercentEscaper(PercentEscaper.SAFEPATHCHARS_URLENCODER, false)
									.escape(id))
					.getResult();
			boolean htmlRequested =
					responseFormat.equals(Accept.Format.HTML.queryParamString);
			if (htmlRequested) {
				return itemJson == null ? notFound(details_item.render(id, ""))
						: ok(details_item.render(id, itemJson.toString()));
			}
			return itemJson == null ? notFound("\"Not found: " + id + "\"")
					: responseFor(itemJson, responseFormat);
		});
		cacheOnRedeem(cacheId, promise, ONE_DAY);
		return promise;
	}

	private static void flashError(boolean badRequest) {
		if (badRequest) {
			flash("warning",
					"Ungültige Suchanfrage. Maskieren Sie Sonderzeichen mit "
							+ "<code>\\</code>. Siehe auch <a href=\""
							+ "https://www.elastic.co/guide/en/elasticsearch/reference/2.4/"
							+ "query-dsl-query-string-query.html#query-string-syntax\">"
							+ "Dokumentation der unterstützten Suchsyntax</a>.");
		} else {
			flash("error",
					"Es ist ein Fehler aufgetreten. "
							+ "Bitte versuchen Sie es erneut oder kontaktieren Sie das "
							+ "Entwicklerteam, falls das Problem fortbesteht "
							+ "(siehe Link 'Feedback' oben rechts).");
		}
	}

	private static void cacheOnRedeem(final String cacheId,
			final Promise<Result> resultPromise, final int duration) {
		resultPromise.onRedeem((Result result) -> {
			if (result.status() == Http.Status.OK)
				Cache.set(cacheId, resultPromise, duration);
		});
	}

	private static Status responseFor(JsonNode responseJson,
			String responseFormat) throws JsonProcessingException {
		String content = "";
		String contentType = "";
		switch (responseFormat) {
		case "rdf": {
			content = RdfConverter.toRdf(responseJson.toString(), RdfFormat.RDF_XML);
			contentType = Accept.Format.RDF_XML.types[0];
			break;
		}
		case "ttl": {
			content = RdfConverter.toRdf(responseJson.toString(), RdfFormat.TURTLE);
			contentType = Accept.Format.TURTLE.types[0];
			break;
		}
		case "nt": {
			content = RdfConverter.toRdf(responseJson.toString(), RdfFormat.N_TRIPLE);
			contentType = Accept.Format.N_TRIPLE.types[0];
			break;
		}
		default: {
			content = new ObjectMapper().writerWithDefaultPrettyPrinter()
					.writeValueAsString(responseJson);
			contentType = Accept.Format.JSON_LD.types[0];
		}
		}
		return content.isEmpty() ? internalServerError("No content")
				: ok(content).as(contentType + "; charset=utf-8");
	}

	private static void addCorsHeader() {
		response().setHeader("Access-Control-Allow-Origin", "*");
	}

	private static void uncache(List<String> ids) {
		for (String id : ids) {
			Cache.remove(String.format("%s-/resources/%s", session("uuid"), id));
		}
	}

	/**
	 * @param q Query to search in all fields
	 * @param agent Query for a agent associated with the resource
	 * @param name Query for the resource name (title)
	 * @param subject Query for the resource subject
	 * @param id Query for the resource id
	 * @param publisher Query for the resource publisher
	 * @param issued Query for the resource issued year
	 * @param medium Query for the resource medium
	 * @param from The page start (offset of page of resource to return)
	 * @param size The page size (size of page of resource to return)
	 * @param owner Owner filter for resource queries
	 * @param t Type filter for resource queries
	 * @param field The facet field (the field to facet over)
	 * @param sort Sorting order for results ("newest", "oldest", "" -> relevance)
	 * @param word The 'word' query, a concept from the hbz union catalog
	 * @param location A single "lat,lon" point or space delimited points polygon
	 * @param nested The nested object path. If non-empty, use q as nested query
	 * @param filter A filter to apply to the query, supports query string syntax
	 * @return The search results
	 */
	public static Promise<Result> facets(String q, String agent, String name,
			String subject, String id, String publisher, String issued, String medium,
			int from, int size, String owner, String t, String field, String sort,
			String word, String location, String nested, String filter) {

		String key = String.format("facets.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s",
				field, q, agent, name, id, publisher, word, subject, issued, medium,
				owner, t, nested);
		Result cachedResult = (Result) Cache.get(key);
		if (cachedResult != null) {
			return Promise.promise(() -> cachedResult);
		}

		String labelTemplate = "<span class='%s'/>&nbsp;%s (%s)";

		Function<JsonNode, Pair<JsonNode, String>> toLabel = json -> {
			String term = json.get("key").asText();
			int count = json.get("doc_count").asInt();
			String icon = Lobid.facetIcon(Arrays.asList(term), field);
			String label = Lobid.facetLabel(Arrays.asList(term), field, "");
			String fullLabel = String.format(labelTemplate, icon, label, count);
			return Pair.of(json, fullLabel);
		};

		Predicate<Pair<JsonNode, String>> labelled = pair -> {
			JsonNode json = pair.getLeft();
			String label = pair.getRight();
			int count = json.get("doc_count").asInt();
			return (!label.contains("http") || label.contains("nwbib")) && label
					.length() > String.format(labelTemplate, "", "", count).length();
		};

		Collator collator = Collator.getInstance(Locale.GERMAN);
		Comparator<Pair<JsonNode, String>> sorter = (p1, p2) -> {
			String t1 = p1.getLeft().get("key").asText();
			String t2 = p2.getLeft().get("key").asText();
			boolean t1Current =
					current(subject, agent, medium, owner, t, field, t1, location);
			boolean t2Current =
					current(subject, agent, medium, owner, t, field, t2, location);
			if (t1Current == t2Current) {
				if (!field.equals(ISSUED_FIELD)) {
					Integer c1 = p1.getLeft().get("doc_count").asInt();
					Integer c2 = p2.getLeft().get("doc_count").asInt();
					return c2.compareTo(c1);
				}
				String l1 = p1.getRight().substring(p1.getRight().lastIndexOf('>') + 1);
				String l2 = p2.getRight().substring(p2.getRight().lastIndexOf('>') + 1);
				return collator.compare(l1, l2);
			}
			return t1Current ? -1 : t2Current ? 1 : 0;
		};

		Function<Pair<JsonNode, String>, String> toHtml = pair -> {
			JsonNode json = pair.getLeft();
			String fullLabel = pair.getRight();
			String term = json.get("key").asText();
			String mediumQuery = !field.equals(MEDIUM_FIELD) //
					? medium
					: queryParam(medium, term);
			String typeQuery = !field.equals(TYPE_FIELD) //
					? t
					: queryParam(t, term);
			String ownerQuery = !field.equals(OWNER_AGGREGATION) //
					? owner
					: withoutAndOperator(queryParam(owner, term));
			String subjectQuery = !field.equals(SUBJECT_FIELD) //
					? subject
					: queryParam(subject, term);
			String agentQuery = !field.equals(AGENT_FIELD) //
					? agent
					: queryParam(agent, term);
			String issuedQuery = !field.equals(ISSUED_FIELD) //
					? issued
					: queryParam(issued, term);
			String locationQuery = !field.equals(Search.SPATIAL_GEO_FIELD) //
					? location
					: queryParam(location, term);

			boolean current =
					current(subject, agent, medium, owner, t, field, term, location);

			String routeUrl = routes.Application.query(q, agentQuery, name,
					subjectQuery, id, publisher, issuedQuery, mediumQuery, from, size,
					ownerQuery, typeQuery, sort(sort, subjectQuery), word, null, field,
					locationQuery, nested, filter).url();

			String result = String.format("<li " + (current ? "class=\"active\"" : "")
					+ "><a class=\"%s-facet-link\" href='%s'>"
					+ "<input onclick=\"location.href='%s'\" class=\"facet-checkbox\" "
					+ "type=\"checkbox\" %s>&nbsp;%s</input>" + "</a></li>",
					Math.abs(field.hashCode()), routeUrl, routeUrl,
					current ? "checked" : "", fullLabel);

			return result;
		};

		Promise<Result> promise =
				query(q, agent, name, subject, id, publisher, issued, medium, from,
						size, owner, t, sort, word, "json", field, location, nested, filter)
								.map(result -> {
									JsonNode json = Json.parse(Helpers.contentAsString(result))
											.get("aggregation");
									Stream<JsonNode> stream =
											StreamSupport.stream(Spliterators.spliteratorUnknownSize(
													json.get(field).elements(), 0), false);
									String labelKey = String.format(
											"facets-labels.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s.%s",
											field, q, agent, name, id, publisher, word, subject,
											issued, medium,
											field.equals(OWNER_AGGREGATION) ? "" : owner, t);
									@SuppressWarnings("unchecked")
									List<Pair<JsonNode, String>> labelledFacets =
											(List<Pair<JsonNode, String>>) Cache.get(labelKey);
									if (labelledFacets == null) {
										labelledFacets = stream.map(toLabel).filter(labelled)
												.collect(Collectors.toList());
										Cache.set(labelKey, labelledFacets, ONE_DAY);
									}
									return labelledFacets.stream().sorted(sorter).map(toHtml)
											.collect(Collectors.toList());
								}).map(lis -> ok(String.join("\n", lis)));
		promise.onRedeem(r -> Cache.set(key, r, ONE_DAY));
		return promise;
	}

	private static String sort(String sort, String subjectQuery) {
		return subjectQuery.contains(",") ? "" /* relevance */ : sort;
	}

	private static boolean current(String subject, String agent, String medium,
			String owner, String t, String field, String term, String location) {
		return field.equals(MEDIUM_FIELD) && contains(medium, term)
				|| field.equals(TYPE_FIELD) && contains(t, term)
				|| field.equals(OWNER_AGGREGATION) && contains(owner, term)
				|| field.equals(SUBJECT_FIELD) && contains(subject, term)
				|| field.equals(AGENT_FIELD) && contains(agent, term)
				|| field.equals(Search.SPATIAL_GEO_FIELD) && contains(location, term);
	}

	private static boolean contains(String value, String term) {
		return Arrays.asList(value.split(",")).contains(term);
	}

	private static String queryParam(String currentParam, String term) {
		if (currentParam.isEmpty())
			return term;
		else if (contains(currentParam, term)) {
			String termRemoved = currentParam.replace(term, "")
					.replaceAll("\\A,|,?\\z", "").replaceAll(",+", ",");
			return termRemoved.equals("AND") ? "" : termRemoved;
		} else
			return withoutAndOperator(currentParam) + "," + term + ",AND";
	}

	private static String withoutAndOperator(String currentParam) {
		return currentParam.replace(",AND", "");
	}

	/**
	 * @param id The resource ID
	 * @return True, if the resource with given ID is starred by the user
	 */
	public static boolean isStarred(String id) {
		return starredIds().contains(id);
	}

	/**
	 * @param id The resource ID to star
	 * @return An OK result
	 */
	public static Promise<Result> star(String id) {
		return Promise.promise(() -> {
			String starred = currentlyStarred();
			if (!starred.contains(id)) {
				session(STARRED, starred + " " + id);
				uncache(Arrays.asList(id));
			}
			return ok("Starred: " + id);
		});
	}

	/**
	 * @param ids The resource IDs to star
	 * @return A 303 SEE_OTHER result to the referrer
	 */
	public static Promise<Result> starAll(String ids) {
		Arrays.asList(ids.split(",")).forEach(id -> star(id));
		return Promise.promise(() -> seeOther(request().getHeader(REFERER)));
	}

	/**
	 * @param id The resource ID to unstar
	 * @return An OK result
	 */
	public static Promise<Result> unstar(String id) {
		return Promise.promise(() -> {
			List<String> starred = starredIds();
			starred.remove(id);
			session(STARRED, String.join(" ", starred));
			uncache(Arrays.asList(id));
			return ok("Unstarred: " + id);
		});
	}

	/**
	 * @param format The format to show the current stars in
	 * @param ids Comma-separated IDs to show, of empty string
	 * @return A page with all resources starred by the user
	 */
	public static Promise<Result> showStars(String format, String ids) {
		final List<String> starred = starredIds();
		if (ids.isEmpty() && !starred.isEmpty()) {
			return Promise.pure(redirect(routes.Application.showStars(format,
					starred.stream().collect(Collectors.joining(",")))));
		}
		final List<String> starredIds =
				starred.isEmpty() && ids.trim().isEmpty() ? starred
						: Arrays.asList(ids.split(","));
		String cacheKey = "starsForIds." + starredIds;
		Object cachedJson = Cache.get(cacheKey);
		if (cachedJson != null && cachedJson instanceof List) {
			@SuppressWarnings("unchecked")
			List<JsonNode> json = (List<JsonNode>) cachedJson;
			return Promise.pure(ok(stars.render(starredIds, json, format)));
		}
		List<JsonNode> vals = starredIds.stream()
				.map(id -> new Search.Builder().build().getResource(id).getResult())
				.collect(Collectors.toList());
		uncache(starredIds);
		Cache.set(cacheKey, vals, ONE_DAY);
		return Promise.pure(ok(stars.render(starredIds, vals, format)));
	}

	/**
	 * @param ids The ids of the resources to unstar, or empty string to clear all
	 * @return If ids is empty: an OK result to confirm deletion of all starred
	 *         resources; if ids are given: A 303 SEE_OTHER result to the referrer
	 */
	public static Promise<Result> clearStars(String ids) {
		if (ids.isEmpty()) {
			uncache(starredIds());
			session(STARRED, "");
			return Promise.promise(
					() -> ok(stars.render(starredIds(), Collections.emptyList(), "")));
		}
		Arrays.asList(ids.split(",")).forEach(id -> unstar(id));
		return Promise.promise(() -> seeOther(request().getHeader(REFERER)));
	}

	/**
	 * @param path The path to redirect to
	 * @return A 301 MOVED_PERMANENTLY redirect to the path
	 */
	public static Promise<Result> redirectTo(String path) {
		return Promise.promise(() -> movedPermanently("/" + path));
	}

	/**
	 * @return The space-delimited IDs of the currently starred resouces
	 */
	public static String currentlyStarred() {
		String starred = session(STARRED);
		return starred == null ? "" : starred.trim();
	}

	private static List<String> starredIds() {
		return new ArrayList<>(Arrays.asList(currentlyStarred().split(" ")).stream()
				.filter(s -> !s.trim().isEmpty()).collect(Collectors.toList()));
	}

	/**
	 * @return JSON-LD context
	 */
	public static Result context() {
		return staticJsonld("context");
	}

	/**
	 * See https://www.w3.org/TR/dwbp/#metadata
	 * 
	 * @param format The format ("json" or "html")
	 * 
	 * @return JSON-LD dataset metadata
	 */
	public static Result dataset(String format) {
		String responseFormat = Accept.formatFor(format, request().acceptedTypes());
		return responseFormat.matches(Format.JSON_LD.queryParamString)
				? staticJsonld("dataset")
				: ok(dataset.render(Json.parse(readFile("dataset"))));
	}

	private static Result staticJsonld(String name) {
		response().setContentType("application/ld+json");
		addCorsHeader();
		try {
			Callable<Status> readContext = () -> ok(readFile(name));
			return Cache.getOrElse(name, readContext, ONE_DAY);
		} catch (Exception e) {
			e.printStackTrace();
			return internalServerError(e.getMessage());
		}
	}

	private static String readFile(String name) {
		try {
			return Streams
					.readAllLines(Play.application().resourceAsStream(name + ".jsonld"))
					.stream().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
