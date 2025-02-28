/* Copyright 2018-2021 Pascal Christoph, hbz. Licensed under the Eclipse Public License 1.0 */

package org.lobid.resources;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.jsonldjava.utils.JsonUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.metafacture.framework.ObjectReceiver;
import org.metafacture.framework.annotations.In;
import org.metafacture.framework.annotations.Out;
import org.metafacture.framework.helpers.DefaultObjectPipe;

import de.hbz.lobid.helper.EtikettMaker;

/**
 * Enriches a JSON document using Etikett. Thus every's object "id" will have a
 * "label". Optionally creates a JSON-LD context from the labels.
 *
 * @author Pascal Christoph (dr0i)
 */
@In(String.class)
@Out(String.class)
public final class EtikettJson
    extends DefaultObjectPipe<String, ObjectReceiver<String>> {
  private static final Logger LOG = LoggerFactory.getLogger(EtikettJson.class);
  private String labelsDirectoryName = "labels";
  private String contextFilenameLocation;
  private boolean generateContext = false;
  private EtikettMaker etikettMaker;
  private boolean pretty = false;

  @Override
  public void onSetReceiver() {
    File file = null;
    if (labelsDirectoryName.startsWith("..")) {
      file = new File(labelsDirectoryName);
    } else {
      file = new File(Thread.currentThread().getContextClassLoader()
          .getResource(labelsDirectoryName).getFile());
    }
    etikettMaker = new EtikettMaker(file);
    if (generateContext) {
      etikettMaker.setContextLocation(contextFilenameLocation);
      etikettMaker.writeContext();
    }
  }

  @Override
  public void process(final String json) {
    if (json.isEmpty()) {
      return;
    }
    try {
      Map<String, Object> jsonMap =
          (Map<String, Object>) JsonUtils.fromInputStream(
              new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      if (jsonMap.get("id") != null)
        getReceiver().process(getEtikettForEveryUri(jsonMap));
      else
        LOG.warn("jsonMap without id, ignoring");
    } catch (IOException e) {
      LOG.error("Couldn't etikett json. %s", e.toString());
    }
  }

  @Override
  protected void onCloseStream() {
    etikettMaker.close();
  }

  private String getEtikettForEveryUri(final Map<String, Object> jsonMap)
      throws IOException {
      // don't label the root id
      Iterator<String> it = jsonMap.keySet().iterator();
      while (it.hasNext()) {
          String key = it.next();
          getAllNodesRecursivelyAndLabelThemIfNecessary(jsonMap, key);
      }
      return pretty ? JsonUtils.toPrettyString(jsonMap)
          : JsonUtils.toString(jsonMap);
  }

  private void getAllNodesRecursivelyAndLabelThemIfNecessary(Map<String, Object> jsonMap, String key) {
      if (jsonMap.get(key) instanceof ArrayList) {
          ((ArrayList) jsonMap.get(key))//
              .stream().filter(e -> (e instanceof LinkedHashMap))
              .forEach(e -> labelNodesWithKeyId((Map<String, Object>) e));
      }
      else if (jsonMap.get(key) instanceof LinkedHashMap) {
          labelNodesWithKeyId((Map<String, Object>) jsonMap.get(key));
      }
  }

    @SuppressWarnings({ "rawtypes", "unchecked" })
  private Map<String, Object> labelNodesWithKeyId(Map<String, Object> jsonMap) {
    Iterator<String> it = jsonMap.keySet().iterator();
    boolean hasLabel = false;
    String id = null;
    while (it.hasNext()) {
      String key = it.next();
      if (key.equals("label")) {
          hasLabel = true;
      }
      else if (!hasLabel && key.equals("id")) {
          id = (String) jsonMap.get(key);
      }
      else {
          getAllNodesRecursivelyAndLabelThemIfNecessary(jsonMap, key);
      }
    }
    if (id != null && !(hasLabel))
      jsonMap.put("label", etikettMaker.getEtikett(id).label);
    return jsonMap;
  }

  /**
   * Sets the name of the directory of the label(s). Will be used to create
   * jsonld-context.
   *
   * @param DIR_TO_LABELS the directory ehre the labels reside
   *
   */
  public void setLabelsDirectoryName(final String DIR_TO_LABELS) {
    this.labelsDirectoryName = DIR_TO_LABELS;
  }

  /**
   * Sets the name of the file of the context that is generated from the labels.
   *
   * @param FILENAME_OF_CONTEXT the directory wehre the labels reside
   *
   */
  public void setFilenameOfContext(final String FILENAME_OF_CONTEXT) {
    contextFilenameLocation = FILENAME_OF_CONTEXT;
  }

  /**
   * Generate the context from labels and store it.
   *
   * @param GENERATE_CONTEXT if true generate the context. Defaults to false.
   *
   */
  public void setGenerateContext(final boolean GENERATE_CONTEXT) {
    generateContext = GENERATE_CONTEXT;
  }

  /**
   * Result pretty json or jsonl (json in one line). Defaults to jsonl.
   *
   * @param PRETTY if true pretty json is generated
   *
   */
  public void setPretty(final boolean PRETTY) {
    pretty = PRETTY;
  }
}
