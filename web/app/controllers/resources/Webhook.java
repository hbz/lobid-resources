/* Copyright 2021 hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.lobid.resources.run.AlmaMarcXml2lobidJsonEs;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * Webhook listener starting update/basedump process for the Alma ETL. Also use
 * to switch ES index alias. Reloads "webhook" configs dynamically, i.e. every
 * time a webhook is called.
 * 
 * @author Pascal Christoph (dr0i)
 */
public class Webhook extends Controller {
  private static final String RESOURCES_CONF = "conf/resources.conf";
  private static final String ETL_OF = "ETL of ";
  private static String filenameUpdate;
  private static String filenameBasedump;
  private static String indexNameOfBasedump;
  private static String basedumpSwitchAutomatically;
  private static String basedumpSwitchMindocs;
  private static String basedumpSwitchMinsize;
  private static String indexNameOfUpdate;
  private static String token;
  private static String indexUpdateAliasSufix = "NOALIAS";
  private static String indexBasedumpAliasSuffix = "-staging";
  private static String alias1;
  private static String alias2;
  private static final String UPDATE_NEWEST_INDEX = "exact";
  private static final String CREATE_INDEX = "create";
  private static final String MSG_ETL_PROCESS_IS_ALREADY_RUNNING =
      " because an ETL process is already running. Try again later!";
  private static final String MSG_UPDATE_ALREADY_RUNNING =
      "Couldn't update index '" + indexNameOfUpdate
          + MSG_ETL_PROCESS_IS_ALREADY_RUNNING;
  private static String createIndexNameOfBasedump = "dummy";
  private static final String MSG_CREATE_INDEX_ALREADY_RUNNING =
      "Couldn't create new index with name '%s' "
          + MSG_ETL_PROCESS_IS_ALREADY_RUNNING;
  private static final String MORPH_FILENAME = "alma.xml";
  // If null, create default values from Global settings
  public static String clusterHost = null;
  public static String clusterName = null;
  private static String msgWrongToken =
      "'%s' is the wrong token. Declining to ETL %s.";
  private static String msgStartEtl = "Starting ETL of '%s'...";
  public static String triggerWebhookUrl;
  public static String triggerWebhookData;

  public Webhook() {
  }

  /**
   * Triggers ETL of updates.
   * 
   * @param GIVEN_TOKEN the token to authorize updating
   * @return "200 ok" or "403 forbidden" (depending on token) or "423 locked" in
   *         case of an already triggered process that was not yet finished
   */
  public static Result updateAlma(final String GIVEN_TOKEN) {
    reloadConfigs();
    final String KIND = "update";
    if (!GIVEN_TOKEN.equals(token)) {
      return wrongToken(KIND, GIVEN_TOKEN);
    }
    if (AlmaMarcXml2lobidJsonEs.threadAlreadyStarted) {
      AlmaMarcXml2lobidJsonEs.sendMail(ETL_OF + KIND, false,
          MSG_UPDATE_ALREADY_RUNNING);
      return status(423, MSG_UPDATE_ALREADY_RUNNING);
    }
    Logger.info(String.format(msgStartEtl, KIND));
    AlmaMarcXml2lobidJsonEs.setKindOfEtl(KIND);
    AlmaMarcXml2lobidJsonEs.setSwitchAliasAfterETL(false);
    AlmaMarcXml2lobidJsonEs.main(filenameUpdate, indexNameOfUpdate,
        indexUpdateAliasSufix, clusterHost, clusterName, UPDATE_NEWEST_INDEX,
        MORPH_FILENAME);
    AlmaMarcXml2lobidJsonEs.sendMail(ETL_OF + KIND, true,
        "Going to update index '" + indexNameOfUpdate + "'");
    return ok("... started ETL " + KIND);
  }

  private static void reloadConfigs() {
    Config config = ConfigFactory.parseFile(new File(RESOURCES_CONF)).resolve();
    Logger.info("reload configs:" + RESOURCES_CONF);
    filenameUpdate = config.getString("webhook.alma.update.filename");
    filenameBasedump = config.getString("webhook.alma.basedump.filename");
    indexNameOfBasedump = config.getString("webhook.alma.basedump.indexname");
    basedumpSwitchAutomatically =
        config.getString("webhook.alma.basedump.switch.automatically");
    basedumpSwitchMindocs =
        config.getString("webhook.alma.basedump.switch.minDocs");
    basedumpSwitchMinsize =
        config.getString("webhook.alma.basedump.switch.minSize");
    indexNameOfUpdate = config.getString("webhook.alma.update.indexname");
    token = config.getString("webhook.alma.token");
    alias1 = indexNameOfBasedump;
    alias2 = indexNameOfBasedump + indexBasedumpAliasSuffix;
    AlmaMarcXml2lobidJsonEs.setTriggerWebhookUrl(triggerWebhookUrl);
    AlmaMarcXml2lobidJsonEs.setTriggerWebhookData(triggerWebhookData);
  }

  /**
   * Triggers ETL of basedump.
   * 
   * @param GIVEN_TOKEN the token to authorize updating
   * @return "200 ok" or "403 forbidden" (depending on token) or "423 locked" in
   *         case of an already triggered process that was not yet finished
   */
  public static Result basedumpAlma(final String GIVEN_TOKEN) {
    reloadConfigs();
    final String KIND = "basedump";
    if (!GIVEN_TOKEN.equals(token)) {
      return wrongToken(KIND, GIVEN_TOKEN);
    }
    createIndexNameOfBasedump = indexNameOfBasedump + "-" + LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-kkmm"));
    if (AlmaMarcXml2lobidJsonEs.threadAlreadyStarted) {
      AlmaMarcXml2lobidJsonEs.sendMail(ETL_OF + KIND, false, String
          .format(MSG_CREATE_INDEX_ALREADY_RUNNING, createIndexNameOfBasedump));
      return status(423, MSG_CREATE_INDEX_ALREADY_RUNNING);
    }
    Logger.info(String.format(msgStartEtl, KIND));
    AlmaMarcXml2lobidJsonEs.setKindOfEtl(KIND);
    if (basedumpSwitchAutomatically.equals("true")) {
      AlmaMarcXml2lobidJsonEs.setSwitchAliasAfterETL(true);
      AlmaMarcXml2lobidJsonEs.setSwitchVariables(alias1, alias2, clusterHost,
          basedumpSwitchMindocs, basedumpSwitchMinsize);
    }
    AlmaMarcXml2lobidJsonEs.main(filenameBasedump, createIndexNameOfBasedump,
        indexBasedumpAliasSuffix, clusterHost, clusterName, CREATE_INDEX,
        MORPH_FILENAME);
    AlmaMarcXml2lobidJsonEs.sendMail(ETL_OF + KIND, true,
        "Going to created new index with name " + createIndexNameOfBasedump
            + " , adding " + indexBasedumpAliasSuffix + " to alias of index");
    return ok("... started ETL " + KIND);
  }

  /**
   * Switches alias of index.
   * 
   * @param GIVEN_TOKEN the token to authorize updating*
   * @return "403 forbidden" (depending on token) or "200 ok" if alias could be
   *         switched, otherwise a "500 internalServerError"
   */

  public static Result switchEsAlias(final String GIVEN_TOKEN) {
    reloadConfigs();
    String msg = "switch aliases '" + alias1 + "' with '" + alias2 + "'";
    if (!GIVEN_TOKEN.equals(token)) {
      return wrongToken(msg, GIVEN_TOKEN);
    }
    Logger.info("start " + msg);
    boolean success = false;
    if (AlmaMarcXml2lobidJsonEs.threadAlreadyStarted) {
      AlmaMarcXml2lobidJsonEs.sendMail("Fail " + msg, false, String
          .format(MSG_CREATE_INDEX_ALREADY_RUNNING, createIndexNameOfBasedump));
      return status(423, String.format(MSG_CREATE_INDEX_ALREADY_RUNNING,
          createIndexNameOfBasedump));
    }
    AlmaMarcXml2lobidJsonEs.setSwitchVariables(alias1, alias2, clusterHost,
        basedumpSwitchMindocs, basedumpSwitchMinsize);
    success = AlmaMarcXml2lobidJsonEs.switchAlias();
    if (success) {
      msg = AlmaMarcXml2lobidJsonEs.MSG_SUCCESS + msg;
      return ok(msg);
    }
    msg = AlmaMarcXml2lobidJsonEs.MSG_FAIL + msg;
    return internalServerError(msg);
  }

  private static Result wrongToken(final String KIND,
      final String GIVEN_TOKEN) {
    String msg = String.format(msgWrongToken, GIVEN_TOKEN, KIND);
    Logger.error(msg);
    AlmaMarcXml2lobidJsonEs.sendMail(KIND, false, msg);
    return forbidden(msg);
  }

}
