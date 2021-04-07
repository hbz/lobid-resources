/* Copyright 2021 hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import play.mvc.Controller;
import play.mvc.Result;
import org.lobid.resources.run.AlmaMarcXml2lobidJsonEs;
import play.Logger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
  private static final String CREATE_INDEX_NAME = INDEX_NAME + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-kkmm"));
  private static final String MORPH_FILENAME = "alma.xml";
  // If null, create default values from Global settings
  public static String clusterHost = null;
  public static String clusterName = null;
  private static String msgWrongToken = "'%s' is the wrong token. Declining to ETL %s.";
  private static String msgStartEtl = "Starting ETL of '%s'...";
  private static String msgEtlFailed = "ETL of '%s' failed: %s";

  /**
   * Triggers ETL of updates.
   * 
   * @param token the token to authorize updating
   * @return 200 ok or 403 forbidden response (depending on token) or 500 in
   *         case of internal server error
   */
  public static Result updateAlma(final String GIVEN_TOKEN) {
    final String KIND="update";
    if (!GIVEN_TOKEN.equals(TOKEN)) {
      return wrongToken(KIND, GIVEN_TOKEN);
    }
    try {
      Logger.info(String.format(msgStartEtl, KIND));
      AlmaMarcXml2lobidJsonEs.main(FILENAME_UPDATE, INDEX_NAME,
      INDEX_UPDATE_ALIAS_SUFFIX, clusterHost, clusterName, UPDATE_NEWEST_INDEX,
          MORPH_FILENAME);
    } catch (Exception e) {
        return etlFailed(KIND, e.toString());
    }
    sendMail(KIND, true, "Updated index '"+ INDEX_NAME + "'");
    return ok("... finished ETL " + KIND);
  }

 /**
   * Triggers ETL of basedump.
   * 
   * @param token the token to authorize updating
   * @return 200 ok or 403 forbidden response (depending on token) or 500 in
   *         case of internal server error
   */
  public static Result basedumpAlma(final String GIVEN_TOKEN) {
    final String KIND="basedump";
    if (!GIVEN_TOKEN.equals(TOKEN)) {
      return wrongToken(KIND, GIVEN_TOKEN);
    }
    try {
      Logger.info(String.format(msgStartEtl, KIND));
      AlmaMarcXml2lobidJsonEs.main(FILENAME_BASEDUMP, CREATE_INDEX_NAME,
      INDEX_BASEDUMP_ALIAS_SUFFIX, clusterHost, clusterName, CREATE_INDEX,
          MORPH_FILENAME);
    } catch (Exception e) {
        return etlFailed(KIND, e.toString());
    }
    sendMail(KIND, true, "Created new index with name "+ CREATE_INDEX_NAME + " , adding " + INDEX_BASEDUMP_ALIAS_SUFFIX +" to alias of index");
    return ok("... finished ETL " + KIND);
  }

  private static Result wrongToken (final String KIND, final String TOKEN) {
    String msg = String.format(msgWrongToken, TOKEN, KIND);
    Logger.error(msg);
    sendMail(KIND, false, msg);
    return forbidden(msg);
  }

  private static Result etlFailed (final String KIND, final String ERROR) {
    String msg = String.format(msgEtlFailed, KIND, ERROR);
    Logger.error(msg);
    sendMail(KIND, false, msg);
    return internalServerError(msg);
  }

  private static void sendMail(final String KIND, final boolean SUCCESS, final String MESSAGE) {
    try {
      Email.sendEmail("hduser", EMAIL, "Webhook ETL of " + KIND + " " + (SUCCESS ? "success :)" : "fails :("), MESSAGE);
    } catch (Exception e) {
        Logger.error("Couldn't send email", e.toString());
    }
  }

}