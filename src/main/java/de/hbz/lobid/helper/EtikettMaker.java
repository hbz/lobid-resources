/* Copyright (c) 2015,2016 "hbz". Licensed under the EPL 2.0 */

package de.hbz.lobid.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeBase;

/**
 * @author Jan Schnasse
 * @author Pascal Christoph (dr0i)
 */
public class EtikettMaker implements EtikettMakerInterface {

    private static final String TYPE = "type";
    private static final String ID = "id";
    private String contextLocation = "web/conf/context.jsonld";

    final static Logger logger = LoggerFactory.getLogger(EtikettMaker.class);

    /**
     * A map with URIs as key
     */
    Map<String, Etikett> pMap = new HashMap<>();

    /**
     * A map with Shortnames as key
     */
    Map<String, Etikett> nMap = new HashMap<>();

    /**
     * The context will be loaded on startup. You can reload the context with POST
     * /utils/reloadContext
     */
    Map<String, Object> context = new HashMap<>();

    /**
     * The labels will be loaded on startup. You can reload the context with POST
     * /utils/reloadLabels
     */
    List<Etikett> labels = new ArrayList<>();

    /**
     * The profile provides a json context and labels
     *
     * @param labelIn input stream to a labels file
     */
    public EtikettMaker(InputStream labelIn) {
        this(new InputStream[]{labelIn});
    }

    /**
     * The profile provides a json context and labels
     *
     * @param labelInArr input stream array to label(s) file(s)
     */
    public EtikettMaker(InputStream[] labelInArr) {
        initMaps(labelInArr);
        initContext();
    }

    /**
     * The file provides a json context and labels. If it's one file this is the
     * labels. If fil is a drirectory every file in it will be merged to one
     * labels.
     *
     * @param labelFile a file to the label(s)
     */
    public EtikettMaker(File labelFile) {
        this(getInputStreamArray(labelFile));
    }

    private static InputStream[] getInputStreamArray(File labelFile) {
        InputStream[] is = null;
        logger.info("use labels directory: " + labelFile.getAbsolutePath());
        try {
            if (labelFile.isDirectory()) {
                File farr[] = labelFile.listFiles();
                is = new InputStream[farr.length];
                for (int i = 0; i < farr.length; i++) {
                    is[i] = new FileInputStream(farr[i]);
                }
            }
            else {
                try (FileInputStream fis = new FileInputStream(labelFile)) {
                    is = new FileInputStream[]{fis};
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return is;
    }

    /*
     * (non-Javadoc)
     *
     * @see de.hbz.lobid.helper.EtikettMakerInterface#getContext()
     */
    @Override
    public Map<String, Object> getContext() {
        return context;
    }

    /*
     * Trying to get a label as sidecar for URIs. First fallback: If an etikett is
     * configured but "label" is missing the "name" will be taken. Second
     * fallback: the domainname (and a possible path) will be extracted from the
     * URI. This domainname is lookuped in the labels. Last fallback: If there is
     * nothing found, the domainname itself will be the label.
     *
     * In the end there will be a label for every URI.
     *
     * @see de.hbz.lobid.helper.EtikettMakerInterface#getEtikett(java.lang.String)
     */
    @Override
    public Etikett getEtikett(String uri) {
        Etikett e = pMap.get(uri);
        if (e == null) {
            e = new Etikett(uri);
            try {
                e.name = getJsonName(uri);
            }
            catch (Exception ex) { // fallback domainname
                logger.debug("no json name available for " + uri
                    + ". Please provide a labels.json file with proper 'name' entry. Using domainname as fallback.");
                String[] uriparts = uri.split("/");
                if (uriparts.length > 2) {
                    String domainname =
                        uriparts[0] + "/" + uriparts[1] + "/" + uriparts[2] + "/";
                    e = pMap
                        .get(uriparts.length > 3 ? domainname + uriparts[3] : domainname);
                }
                if (e == null) { // domainname may have a label
                    e = new Etikett(uri);
                    try {
                        e.name = getJsonName(uri);
                    }
                    catch (Exception exc) {
                        e.name = uriparts[uriparts.length - 1];
                    }
                }
                logger.debug("Fallback is:" + e.label);
            }
        }
        if (e.label == null || e.label.isEmpty()) { // fallback name
            e.label = e.name;
        }
        logger.debug("Etikett for " + uri + " : " + e.label);
        return e;
    }

    private void initContext() {
        context = createContext();
    }

    /**
     * Generates context.json based on labels.json.
     * Stores into filesystem, alphabetically sorted.
     */
    public void writeContext() {
        logger.info("Writing context file ...");
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT) //
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS) //
                .writeValue(new File(getContextLocation()), context);
            logger.info(
                "... done writing context file to " + getContextLocation() + ".");
        }
        catch (Exception e) {
            logger.error("Error during writing context file! ", e);
        }
    }

    private void initMaps(InputStream[] labelInArr) {
        try {
            labels = createLabels(labelInArr);
            for (Etikett etikett : labels) {
                pMap.put(etikett.uri, etikett);
                nMap.put(etikett.name, etikett);
            }
        }
        catch (Exception e) {
            logger.error("", e);
        }
    }

    private static List<Etikett> createLabels(InputStream[] labelInArr) {
        logger.info("Create labels....");
        List<Etikett> result = new ArrayList<>();
        try {
            for (InputStream is : labelInArr) {
                result.addAll(loadFile(is, new ObjectMapper().getTypeFactory()
                    .constructCollectionType(List.class, Etikett.class)));
            }
            logger.info("...succeed!");
        }
        catch (Exception e) {
            logger.warn("...not succeeded!");
        }
        return result;
    }

    Map<String, Object> createContext() {
        Map<String, Object> pmap;
        Map<String, Object> cmap = new HashMap<>();
        for (Etikett l : labels) {
            if ((l.referenceType != null && "class".equals(l.referenceType))
                || l.name == null) {
                continue;
            }
            pmap = new HashMap<>();
            pmap.put("@id", l.uri);
            if (l.referenceType != null && !"String".equals(l.referenceType)) {
                pmap.put("type", l.referenceType);
            }
            if (l.container != null) {
                pmap.put("@container", l.container);
            }
            cmap.put(l.name, pmap);
        }
        cmap.put(ID, "@id");
        Map<String, Object> contextObject = new HashMap<>();
        contextObject.put("@context", cmap);
        return contextObject;
    }

    private static <T> T loadFile(InputStream labelIn, TypeBase type) {
        try (InputStream in = labelIn) {
            return new ObjectMapper().readValue(in, type);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during initialization!", e);
        }
    }

    /**
     * @param predicate
     * @return The short name of the predicate uses String.split on first index of
     * '#' or last index of '/'
     */
    String getJsonName(String predicate) {
        return pMap.get(predicate).name;
    }

    @Override
    public Etikett getEtikettByName(String name) {
        return nMap.get(name);
    }

    @Override
    public Collection<Etikett> getValues() {
        return pMap.values();
    }

    @Override
    public boolean supportsLabelsForValues() {
        return true;
    }

    @Override
    public String getIdAlias() {
        return ID;
    }

    @Override
    public String getTypeAlias() {
        return TYPE;
    }

    @Override
    public String getLabelKey() {
        return "label";
    }

    /**
     * @return filename of the jsonld-context
     */
    @Override
    public String getContextLocation() {
        return contextLocation;
    }

    /**
     * Sets the filename of the jsonld-context.
     *
     * @param contextFname the filename of the jsonld-context
     */
    @Override
    public void setContextLocation(final String contextFname) {
        contextLocation = contextFname;
    }

    public void close(){
        if (pMap!=null) {
            pMap.clear();
        }
        if (nMap!=null) {
            nMap.clear();
        }
        if (context!= null) {
            context.clear();
        }
        if (labels!=null) {
            labels.clear();
        }
    }
}
