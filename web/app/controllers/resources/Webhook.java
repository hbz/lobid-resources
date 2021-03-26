/* Copyright 2021 hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import play.mvc.Controller;
import play.mvc.Result;
import org.lobid.resources.run.AlmaMarcXml2lobidJsonEs;
import play.Logger;

/**
 * Simple webhook listener starting update process for the Alma ETL.
 * 
 * @author Pascal Christoph (dr0i)
 */
public class Webhook extends Controller {
  // If null, create default values from settings
  private static final String FILENAME_UPDATE =
      Application.CONFIG.getString("update.filename");
  private static final String INDEX_NAME =
      Application.CONFIG.getString("update.indexname");
  private static final String TOKEN =
      Application.CONFIG.getString("update.token");
  private static final String INDEX_ALIAS_SUFFIX = "NOALIAS";

  private static final String UPDATE_NEWEST_INDEX = "exact";
  private static final String MORPH_FILENAME = "alma.xml";
  // If null, create default values from Global settings
  public static String clusterHost = null;
  public static String clusterName = null;

  /**
   * Triggers ETL of updates.
   * 
   * @param token the token to authorize updating
   * @return 200 ok or 403 forbidden response (depending on token) or 500 in
   *         case of internal server error
   */
  public static Result updateAlma(final String token) {
    if (!token.equals(TOKEN)) {
      return forbidden("Wrong token. Declining to update.");
    }
    try {
      Logger.info("Starting ETL of updates ...");
      AlmaMarcXml2lobidJsonEs.main(FILENAME_UPDATE, INDEX_NAME,
          INDEX_ALIAS_SUFFIX, clusterHost, clusterName, UPDATE_NEWEST_INDEX,
          MORPH_FILENAME);
    } catch (Exception e) {
      Logger.error("Transformation failed", e);
      return internalServerError(e.toString());
    }
    return ok("... finished ETL updates");
  }
}
