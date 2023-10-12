/*
 *  Copyright 2021, dr0i
 */
package org.lobid.resources;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.metafacture.framework.FluxCommand;
import org.metafacture.framework.ObjectReceiver;
import org.metafacture.framework.annotations.In;
import org.metafacture.framework.annotations.Out;
import org.metafacture.framework.helpers.DefaultObjectPipe;

/**
 * Add Elasticsearch bulk indexing metadata to JSON input.
 * Outputs a Map.
 *
 * @author Fabian Steeg (fsteeg)
 * @author Jens Wille
 *
 */
@In(String.class)
@Out(Map.class)
public class JsonToElasticsearchBulkMap extends
        DefaultObjectPipe<String, ObjectReceiver<HashMap<String,String>>> {

    /**
     * Use a MultiMap with Jackson to collect values from multiple fields with
     * identical names under a single key.
     */
    static class MultiMap extends HashMap<String, Object> {
        private static final long serialVersionUID = 490682490432334605L;

        MultiMap() {
            // default constructor for Jackson
        }

        @Override
        public Object put(String key, Object value) {
            if (containsKey(key)) {
                Object oldValue = get(key);
                if (oldValue instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<Object> vals = ((Set<Object>) oldValue);
                    vals.add(value);
                    return super.put(key, vals);
                }
                HashSet<Object> set = new HashSet<>(Arrays.asList(oldValue, value));
                return super.put(key, set.size() == 1 ? value : set);
            }
            return super.put(key, value);
        }
    }

    private ObjectMapper mapper = new ObjectMapper();
    private String[] idPath;
    private String type;
    private String index;

    /**
     * @param idPath The key path of the JSON value to be used as the ID for the record
     * @param type The Elasticsearch index type
     * @param index The Elasticsearch index name
     */
    public JsonToElasticsearchBulkMap(String[] idPath, String type, String index) {
        this.idPath = idPath;
        this.type = type;
        this.index = index;
    }

    /**
     * @param idKey The key of the JSON value to be used as the ID for the record
     * @param type The Elasticsearch index type
     * @param index The Elasticsearch index name
     */
    public JsonToElasticsearchBulkMap(String idKey, String type, String index) {
        this(new String[]{idKey}, type, index);
    }

    /**
     * @param idKey The key of the JSON value to be used as the ID for the record
     * @param type The Elasticsearch index type
     * @param index The Elasticsearch index name
     * @param entitySeparator The separator between entity names in idKey
     */
    public JsonToElasticsearchBulkMap(String idKey, String type, String index, String entitySeparator) {
        this(idKey.split(Pattern.quote(entitySeparator)), type, index);
    }

    @Override
    public void process(String obj) {
        try {
            Map<String, Object> json = mapper.readValue(obj, MultiMap.class);
            HashMap<String, String> detailsMap = new HashMap<>();
            detailsMap.put("_id", findId(json));
            detailsMap.put("_type", type);
            detailsMap.put("_index", index);
            detailsMap.put("graph", obj);
            getReceiver().process(detailsMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String findId(Object value) {
        if (idPath.length < 1) {
            return null;
        }

        for (final String key : idPath) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> nestedMap = (Map<String, Object>) value;
                value = nestedMap.get(key);
            }
            else {
                return null;
            }
        }

        return value.toString();
    }
}
