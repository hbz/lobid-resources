package de.hbz.lobid.helper;


import org.lobid.resources.ElasticsearchIndexer;
import org.metafacture.framework.annotations.Description;
import org.metafacture.framework.annotations.In;
import org.metafacture.framework.annotations.Out;
import org.metafacture.io.ConfigurableObjectWriter;
import org.metafacture.io.FileCompression;
import org.metafacture.io.ObjectFileWriter;

import java.util.HashMap;

/**
 * Writes json objects to a file.
 *
 * @author Pascal Christoph (dr0i)
 */
@Description("Writes json objects to stdout or a file")
@In(HashMap.class)
@Out(Void.class)
public class JsonFileWriter<T> implements ConfigurableObjectWriter<HashMap<String, String>> {

    private final ConfigurableObjectWriter<HashMap<String, String>> jsonWriter = null;
    private static String destinationPathPrefix;
    private static String suffix;
    private static final String SUFFIX_JSON =".json";

    public JsonFileWriter(String destinationPathPrefix) {
        this(destinationPathPrefix,"");
    }

    public JsonFileWriter(String destinationPathPrefix, String suffix) {
        this.destinationPathPrefix = destinationPathPrefix;
        this.suffix = SUFFIX_JSON+suffix;
    }

    @Override
    public String getEncoding() {
        return jsonWriter.getEncoding();
    }

    @Override
    public void setEncoding(final String encoding) {
        jsonWriter.setEncoding(encoding);
    }

    @Override
    public FileCompression getCompression() {
        return jsonWriter.getCompression();
    }

    @Override
    public void setCompression(final FileCompression compression) {
        jsonWriter.setCompression(compression);
    }

    @Override
    public void setCompression(final String compression) {
        jsonWriter.setCompression(compression);
    }

    @Override
    public String getHeader() {
        return jsonWriter.getHeader();
    }

    @Override
    public void setHeader(final String header) {
        jsonWriter.setHeader(header);
    }

    @Override
    public String getFooter() {
        return jsonWriter.getFooter();
    }

    @Override
    public void setFooter(final String footer) {
        jsonWriter.setFooter(footer);
    }

    @Override
    public String getSeparator() {
        return jsonWriter.getSeparator();
    }

    @Override
    public void setSeparator(final String separator) {
    }

    @Override
    public void process(final HashMap<String, String> json) {
        String filePath = json.get(ElasticsearchIndexer.Properties.ID.getName())+suffix;
        ObjectFileWriter jsonObjectWriter = new ObjectFileWriter<String>(destinationPathPrefix + "/" + filePath);
        jsonObjectWriter.process(json.get(ElasticsearchIndexer.Properties.GRAPH.getName()));
        jsonObjectWriter.closeStream();
    }

    @Override
    public void resetStream() {
        jsonWriter.resetStream();
    }

    /**
     * Not needed - see {@link #process}
     */
    @Override
    public void closeStream() {
    }

}
