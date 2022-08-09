package de.hbz.lobid.helper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.*;

/**
 * Uploads data using {@link URLConnection} with POST method and returns the response.
 *
 * @author Pascal Christoph (dr0i)
 */
public final class HttpPoster {

    public final String CONTENT_TYPE = "Content-Type";
    public final String POST = "POST";
    private String contentType = "application/json";
    private URL url;
    private static final Logger LOG = LogManager.getLogger(HttpPoster.class);

    /**
     * Creates an instance of {@link HttpPoster}.
     */
    public HttpPoster() {
    }

    /**
     * Sets the HTTP contentType header. This is a mime-type such as text/plain,
     * text/html or application/x-ndjson. The default value is "application/json".
     *
     * @param contentType mime-type to use for the HTTP contentType header
     */
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    /**
     * Sets the HTTP URL of the webhook listener to POST to
     *
     * @param url the URL to post to
     */
    public void setUrl(final String url) throws MalformedURLException {
        this.url = new URL(url);
    }

    public void processData(final String data) throws IllegalStateException, NullPointerException, IOException {
        LOG.debug("Going to post data: '" + data + "'");
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) this.url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod(POST);
            conn.setRequestProperty(CONTENT_TYPE, this.contentType);
            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes());
            LOG.info("Got response code: " + conn.getResponseCode());
            if (204 == conn.getResponseCode()) {
                LOG.info("=> Webhook listener successfully triggered");
            } else {
                if (conn.getResponseCode() == 401) LOG.error("=> Client ID not valid");
                if (conn.getResponseCode() == 409)
                    LOG.error("=> Webhook listener already triggered resp. in running mode");
                //  BufferedReader brSucc = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                if (conn.getResponseMessage() != null) {
                    LOG.info("HttpPoster response message" + conn.getResponseMessage());
                }
                /*else {
                    BufferedReader brErr = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    if (brErr != null) {
                        LOG.error("HttpPoster error response " +  brErr.lines().collect(Collectors.joining()));
                    }
                }*/
            }
        } catch (IOException e) {
            throw new IOException(e);
        }

    }
}
