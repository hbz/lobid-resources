/* Copyright 2021 hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import play.mvc.Controller;
import play.mvc.Result;
import org.lobid.resources.run.AlmaMarcXml2lobidJsonEs;
import play.Logger;
import java.time.LocalDate;
import de.hbz.lobid.helper.Email;

/**
 * Simple webhook listener starting update/basedump process for the Alma ETL.
 * 
 * @author Pascal Christoph (dr0i)
 */
public class Webhook extends Controller {
  private static final String FILENAME_UPDATE=
      Application.CONFIG.getString("webhook.alma.update.filename");
  private static final String FILENAME_BASEDUMP=
      Application.CONFIG.getString("webhook.alma.basedump.filename");
  private static final String INDEX_NAME =
      Application.CONFIG.getString("webhook.alma.indexname");
  private static final String TOKEN =
      Application.CONFIG.getString("webhook.alma.token");
  private static final String EMAIL =
      Application.CONFIG.getString("webhook.email");
  private static final String INDEX_UPDATE_ALIAS_SUFFIX = "NOALIAS";
  private static final String INDEX_BASEDUMP_ALIAS_SUFFIX = "-staging";
  private static final String UPDATE_NEWEST_INDEX = "exact";
  private static final String CREATE_INDEX = "create";
  private static final String CREATE_INDEX_NAME = INDEX_NAME + "-" + LocalDate.now();
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
      sendMail("update", false, "wrong token");
      return forbidden("Wrong token. Declining to etl update.");
    }
    try {
      Logger.info("Starting ETL of updates ...");
      AlmaMarcXml2lobidJsonEs.main(FILENAME_UPDATE, INDEX_NAME,
      INDEX_UPDATE_ALIAS_SUFFIX, clusterHost, clusterName, UPDATE_NEWEST_INDEX,
          MORPH_FILENAME);
    } catch (Exception e) {
      Logger.error("Transformation failed", e);
      sendMail("update", false, e.toString());
      return internalServerError("ETL of update failed:" +e.toString());
    }
    sendMail("update", true, ". Updated index with name "+ INDEX_NAME);
    return ok("... finished ETL update!");
  }

 /**
   * Triggers ETL of basedump.
   * 
   * @param token the token to authorize updating
   * @return 200 ok or 403 forbidden response (depending on token) or 500 in
   *         case of internal server error
   */
  public static Result basedumpAlma(final String token) {
    if (!token.equals(TOKEN)) {
      sendMail("basedump", false, "wrong token");
      return forbidden("Wrong token. Declining to etl basedump.");
    }
    try {
      Logger.info("Starting ETL of basedump ...");
      AlmaMarcXml2lobidJsonEs.main(FILENAME_BASEDUMP, CREATE_INDEX_NAME,
      INDEX_BASEDUMP_ALIAS_SUFFIX, clusterHost, clusterName, CREATE_INDEX,
          MORPH_FILENAME);
    } catch (Exception e) {
      Logger.error("Transformation failed", e);
      sendMail("basedump", false, e.toString());
      return internalServerError("ETL of basedump failed:" + e.toString());
    }
    sendMail("basedump", true, ". Created new index with name "+ INDEX_NAME + " , adding " + INDEX_BASEDUMP_ALIAS_SUFFIX +" to alias of index");
    return ok("... finished ETL basedump!");
  }

  private static void sendMail(final String kind, final boolean success, final String message) {
     Email.sendEmail("hduser", EMAIL, "Webhook ETL of " + kind + " " + (success ? "success :)" : "fails :("), message);
  }

}