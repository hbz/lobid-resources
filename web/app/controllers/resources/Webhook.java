/* Copyright 2021 hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import play.mvc.Controller;
import play.mvc.Result;
import org.lobid.resources.run.AlmaMarcXml2lobidJsonEs;
import org.lobid.resources.run.SwitchEsAlmaAlias;

import play.Logger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import de.hbz.lobid.helper.Email;
import java.net.UnknownHostException;

/**
 * Simple webhook listener starting update/basedump process for the Alma ETL.
 * 
 * @author Pascal Christoph (dr0i)
 */
public class Webhook extends Controller {
  private static final String FILENAME_UPDATE =
      Application.CONFIG.getString("webhook.alma.update.filename");
  private static final String FILENAME_BASEDUMP =
      Application.CONFIG.getString("webhook.alma.basedump.filename");
  private static final String INDEX_NAME_OF_BASEDUMP =
      Application.CONFIG.getString("webhook.alma.basedump.indexname");
  private static final String BASEDUMP_SWITCH_MINDOCS =
      Application.CONFIG.getString("webhook.alma.basedump.switch.minDocs");
  private static final String BASEDUMP_SWITCH_MINSIZE =
      Application.CONFIG.getString("webhook.alma.basedump.switch.minSize");
  private static final String INDEX_NAME_OF_UPDATE =
      Application.CONFIG.getString("webhook.alma.update.indexname");
  private static final String TOKEN =
      Application.CONFIG.getString("webhook.alma.token");
  private static final String EMAIL =
      Application.CONFIG.getString("webhook.email");
  private static final String INDEX_UPDATE_ALIAS_SUFFIX = "NOALIAS";
  private static final String INDEX_BASEDUMP_ALIAS_SUFFIX = "-staging";
  private static final String UPDATE_NEWEST_INDEX = "exact";
  private static final String CREATE_INDEX = "create";
  private static final String MSG_ETL_PROCESS_IS_ALREADY_RUNNING =
      " because an ETL process is already running. Try again later!";
  private static final String MSG_UPDATE_ALREADY_RUNNING =
      "Couldn't update index '" + INDEX_NAME_OF_UPDATE
          + MSG_ETL_PROCESS_IS_ALREADY_RUNNING;

  private static final String MORPH_FILENAME = "alma.xml";
  // If null, create default values from Global settings
  public static String clusterHost = null;
  public static String clusterName = null;
  private static String msgWrongToken =
      "'%s' is the wrong token. Declining to ETL %s.";
  private static String msgStartEtl = "Starting ETL of '%s'...";

  /**
   * Triggers ETL of updates.
   * 
   * @param GIVEN_TOKEN the token to authorize updating
   * @return "200 ok" or "403 forbidden" (depending on token) or "423 locked" in
   *         case of an already triggered process that was not yet finished
   */
  public static Result updateAlma(final String GIVEN_TOKEN) {
    final String KIND = "update";
    if (!GIVEN_TOKEN.equals(TOKEN)) {
      return wrongToken(KIND, GIVEN_TOKEN);
    }
    if (AlmaMarcXml2lobidJsonEs.threadAlreadyStarted) {
      sendMail(KIND, false, MSG_UPDATE_ALREADY_RUNNING);
      return status(423, MSG_UPDATE_ALREADY_RUNNING);
    }
    Logger.info(String.format(msgStartEtl, KIND));
    AlmaMarcXml2lobidJsonEs.setKindOfEtl(KIND);
    AlmaMarcXml2lobidJsonEs.setEmail(EMAIL);
    AlmaMarcXml2lobidJsonEs.main(FILENAME_UPDATE, INDEX_NAME_OF_UPDATE,
        INDEX_UPDATE_ALIAS_SUFFIX, clusterHost, clusterName,
        UPDATE_NEWEST_INDEX, MORPH_FILENAME);
    sendMail(KIND, true,
        "Going to update index '" + INDEX_NAME_OF_UPDATE + "'");
    return ok("... started ETL " + KIND);
  }

  /**
   * Triggers ETL of basedump.
   * 
   * @param GIVEN_TOKEN the token to authorize updating
   * @return "200 ok" or "403 forbidden" (depending on token) or "423 locked" in
   *         case of an already triggered process that was not yet finished
   */
  public static Result basedumpAlma(final String GIVEN_TOKEN) {
    final String KIND = "basedump";
    if (!GIVEN_TOKEN.equals(TOKEN)) {
      return wrongToken(KIND, GIVEN_TOKEN);
    }
    final String CREATE_INDEX_NAME_OF_BASEDUMP =
        INDEX_NAME_OF_BASEDUMP + "-" + LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-kkmm"));
    final String MSG_CREATE_INDEX_ALREADY_RUNNING =
        "Couldn't created new index with name " + CREATE_INDEX_NAME_OF_BASEDUMP
            + MSG_ETL_PROCESS_IS_ALREADY_RUNNING;
    if (AlmaMarcXml2lobidJsonEs.threadAlreadyStarted) {
      sendMail(KIND, false, MSG_CREATE_INDEX_ALREADY_RUNNING);
      return status(423, MSG_CREATE_INDEX_ALREADY_RUNNING);
    }
    Logger.info(String.format(msgStartEtl, KIND));
    AlmaMarcXml2lobidJsonEs.setKindOfEtl(KIND);
    AlmaMarcXml2lobidJsonEs.setEmail(EMAIL);
    AlmaMarcXml2lobidJsonEs.main(FILENAME_BASEDUMP,
        CREATE_INDEX_NAME_OF_BASEDUMP, INDEX_BASEDUMP_ALIAS_SUFFIX, clusterHost,
        clusterName, CREATE_INDEX, MORPH_FILENAME);
    sendMail(KIND, true,
        "Going to created new index with name " + CREATE_INDEX_NAME_OF_BASEDUMP

            + " , adding " + INDEX_BASEDUMP_ALIAS_SUFFIX
            + " to alias of index");
    return ok("... started ETL " + KIND);
  }

  /**
   * Triggers ETL of updates.
   * 
   * @param GIVEN_TOKEN the token to authorize updating
   * @return "200 ok" or "403 forbidden" (depending on token) or "423 locked" in
   *         case of an already triggered process that was not yet finished
   */
  public static Result switchEsAlias(final String GIVEN_TOKEN) {
    final String ALIAS1 = INDEX_NAME_OF_BASEDUMP;
    final String ALIAS2 = INDEX_NAME_OF_BASEDUMP + INDEX_BASEDUMP_ALIAS_SUFFIX;

    final String MSG = "switch alias '" + ALIAS1 + "' with '" + ALIAS2 + "'";
    if (!GIVEN_TOKEN.equals(TOKEN)) {
      return wrongToken(MSG, GIVEN_TOKEN);
    }
    Logger.info(MSG);
    boolean success = false;
    try {
      success = SwitchEsAlmaAlias.switchAlias(ALIAS1, ALIAS2, clusterHost,
          BASEDUMP_SWITCH_MINDOCS, BASEDUMP_SWITCH_MINSIZE);
    } catch (UnknownHostException e) {
      Logger.error("Couldn't switch alias", e.toString());
      success = false;
    }
    if (success) {
      sendMail("switching aliases", true, "Going to " + MSG);
      return ok("succeeded to '" + MSG + "'");
    } else
      return internalServerError("failed to '" + MSG + "'");
  }

  private static Result wrongToken(final String KIND,
      final String GIVEN_TOKEN) {
    String msg = String.format(msgWrongToken, GIVEN_TOKEN, KIND);
    Logger.error(msg);
    sendMail(KIND, false, msg);
    return forbidden(msg);
  }

  private static void sendMail(final String KIND, final boolean SUCCESS,
      final String MESSAGE) {
    try {
      Email.sendEmail("hduser", EMAIL, "Webhook ETL of " + KIND + " triggered:"
          + (SUCCESS ? "success :)" : "fails :("), MESSAGE);
    } catch (Exception e) {
      Logger.error("Couldn't send email", e.toString());
    }
  }

}