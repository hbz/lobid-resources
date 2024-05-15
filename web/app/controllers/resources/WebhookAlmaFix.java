/* Copyright 2021 hbz. Licensed under the EPL 2.0 */

package controllers.resources;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.lobid.resources.run.AlmaMarcXmlFix2lobidJsonEs;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Webhook listener starting update/basedump process for the Alma Fix ETL. Also use
 * to switch ES index alias. Reloads "webhook" configs dynamically, i.e. every
 * time a webhook is called.
 *
 * @author Pascal Christoph (dr0i)
 */
public class WebhookAlmaFix extends Controller {
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
  private final static String indexUpdateAliasSufix = "NOALIAS";
  private final static String indexBasedumpAliasSuffix = "-staging";
  private static String alias1;
  private static String alias2;
  private static final String UPDATE_NEWEST_INDEX = "exact";
  private static final String CREATE_INDEX = "create";
  private static final String MSG_ETL_PROCESS_IS_ALREADY_RUNNING =
      " because an ETL process is already running. Try again later!";
  private static final String MSG_UPDATE_ALREADY_RUNNING =
      "Couldn't update index %s'" + MSG_ETL_PROCESS_IS_ALREADY_RUNNING;
  private static final String MSG_FILE_TOO_SMALL = "File size is too small - data seems to be empty";
  private static String createIndexNameOfBasedump = "dummy";
  private static final String MSG_CREATE_INDEX_ALREADY_RUNNING =
      "Couldn't create new index with name '%s' "
          + MSG_ETL_PROCESS_IS_ALREADY_RUNNING;
  private static final String MSG_CALLED_FROM_REMOTE_ADDRESS = "Called from: '%s' ";
  private static final String FIX_FILENAME = "conf/alma/alma.fix";
  // If null, create default values from Global settings
  public static String clusterHost = null;
  public static String clusterName = null;
  private final static String msgWrongToken = "'%s' is the wrong token. Declining to ETL %s.";
  private final static String msgStartEtl = "Starting ETL of '%s'...";
  public static String triggerWebhookUrl;
  public static String triggerWebhookData;

  public WebhookAlmaFix() {
  }

  /**
   * Triggers ETL of updates.
   *
   * @param GIVEN_TOKEN the token to authorize updating
   * @return "200 ok" or "403 forbidden" (depending on token) or "423 locked" in
   *         case of an already triggered process that was not yet finished, or a "500"
   *         if the size of the data file is too small.
   */
  public static Result updateAlma(final String GIVEN_TOKEN) {
    reloadConfigs();
    final String KIND = "update";
    if (!GIVEN_TOKEN.equals(token)) {
      return wrongToken(KIND, GIVEN_TOKEN);
    }
    String msg;
    try {
      msg = composeMessage(MSG_FILE_TOO_SMALL);
      if (Files.size(Paths.get(filenameUpdate.split(";")[0])) < 512) {
        Logger.error(msg);
        AlmaMarcXmlFix2lobidJsonEs.sendMail("Triggering of " + ETL_OF + KIND, false,
            msg);
        Logger.warn(msg);
        return status(500, msg);
      }
    }
    catch (IOException e) {
      msg = composeMessage("Problems with data file\n" + e.getMessage());
      AlmaMarcXmlFix2lobidJsonEs.sendMail("Triggering of " + ETL_OF + KIND, false,
          msg);
      Logger.warn(msg);
      return status(500, msg);
    }
    if (AlmaMarcXmlFix2lobidJsonEs.threadAlreadyStarted) {
      msg = composeMessage(String.format(MSG_UPDATE_ALREADY_RUNNING, indexNameOfUpdate));
      AlmaMarcXmlFix2lobidJsonEs.sendMail(ETL_OF + KIND, false,
          msg);
      Logger.warn(msg);
      return status(423, msg);
    }
    msg = composeMessage(String.format(msgStartEtl, KIND));
    Logger.info(msg);
    AlmaMarcXmlFix2lobidJsonEs.setKindOfEtl(KIND);
    AlmaMarcXmlFix2lobidJsonEs.setSwitchAliasAfterETL(false);
    AlmaMarcXmlFix2lobidJsonEs.main(filenameUpdate, indexNameOfUpdate,
        indexUpdateAliasSufix, clusterHost, clusterName, UPDATE_NEWEST_INDEX,
        FIX_FILENAME);

    AlmaMarcXmlFix2lobidJsonEs.sendMail("Triggering of " + ETL_OF + KIND, true,
        msg + "Going to update index '" + indexNameOfUpdate + "'");
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
    AlmaMarcXmlFix2lobidJsonEs.setTriggerWebhookUrl(triggerWebhookUrl);
    AlmaMarcXmlFix2lobidJsonEs.setTriggerWebhookData(triggerWebhookData);
  }

  /**
   * Triggers ETL of basedump.
   *
   * @param GIVEN_TOKEN the token to authorize updating
   * @return "200 ok" or "403 forbidden" (depending on token) or "423 locked" in
   *         case of an already triggered process that was not yet finished, or a "500"
   *         if the size of the data file is too small.
   */
  public static Result basedumpAlma(final String GIVEN_TOKEN) {
    reloadConfigs();
    final String KIND = "basedump";
    if (!GIVEN_TOKEN.equals(token)) {
      return wrongToken(KIND, GIVEN_TOKEN);
    }
    String msg;
    try {
      if (Files.size(Paths.get(filenameBasedump)) < 512) {
        msg = composeMessage(MSG_FILE_TOO_SMALL);
        Logger.error(msg);
        AlmaMarcXmlFix2lobidJsonEs.sendMail("Triggering of " + ETL_OF + KIND, false,
            msg);
        Logger.warn(msg);
        return status(500, msg);
      }
    }
    catch (IOException e) {
      msg = composeMessage("IO Problems with data file\n" + e);
      Logger.warn(msg);
      return status(500, msg);
    }
    createIndexNameOfBasedump = indexNameOfBasedump + "-" + LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-kkmm"));
    if (AlmaMarcXmlFix2lobidJsonEs.threadAlreadyStarted) {
      msg = composeMessage(String.format(MSG_CREATE_INDEX_ALREADY_RUNNING, createIndexNameOfBasedump));
      AlmaMarcXmlFix2lobidJsonEs.sendMail(ETL_OF + KIND, false, msg);
      Logger.warn(msg);
      return status(423, msg);
    }
    msg = composeMessage(String.format(msgStartEtl, KIND));
    Logger.info(msg);
    AlmaMarcXmlFix2lobidJsonEs.setKindOfEtl(KIND);
    if (basedumpSwitchAutomatically.equals("true")) {
      AlmaMarcXmlFix2lobidJsonEs.setSwitchAliasAfterETL(true);
      AlmaMarcXmlFix2lobidJsonEs.setSwitchVariables(alias1, alias2, clusterHost,
          basedumpSwitchMindocs, basedumpSwitchMinsize);
    }
    AlmaMarcXmlFix2lobidJsonEs.main(filenameBasedump, createIndexNameOfBasedump,
        indexBasedumpAliasSuffix, clusterHost, clusterName, CREATE_INDEX,
        FIX_FILENAME);
    AlmaMarcXmlFix2lobidJsonEs.sendMail(ETL_OF + KIND, true,
        msg + "Going to create new index with name " + createIndexNameOfBasedump
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
    String subject = "switch aliases '" + alias1 + "' with '" + alias2 + "'";
    String msg = composeMessage(subject);
    Logger.info(msg);
    if (!GIVEN_TOKEN.equals(token)) {
      return wrongToken(subject, GIVEN_TOKEN);
    }
    boolean success;
    if (AlmaMarcXmlFix2lobidJsonEs.threadAlreadyStarted) {
      msg = composeMessage(String.format(MSG_CREATE_INDEX_ALREADY_RUNNING, createIndexNameOfBasedump));
      AlmaMarcXmlFix2lobidJsonEs.sendMail("Failed: " + subject, false, msg);
      Logger.warn(msg);
      return status(423, msg);
    }
    AlmaMarcXmlFix2lobidJsonEs.setSwitchVariables(alias1, alias2, clusterHost, basedumpSwitchMindocs, basedumpSwitchMinsize);
    success = AlmaMarcXmlFix2lobidJsonEs.switchAlias();
    if (success) {
      msg = composeMessage(AlmaMarcXmlFix2lobidJsonEs.MSG_SUCCESS + subject);
      Logger.info(msg);
      return ok(msg);
    }
    msg = composeMessage(AlmaMarcXmlFix2lobidJsonEs.MSG_FAIL + subject);
    Logger.warn(msg);
    return internalServerError(msg);
  }

  private static Result wrongToken(final String KIND,
      final String GIVEN_TOKEN) {
    String msg = composeMessage(String.format(msgWrongToken, GIVEN_TOKEN, KIND));
    Logger.warn(msg);
    AlmaMarcXmlFix2lobidJsonEs.sendMail(KIND, false, msg);
    return forbidden(msg);
  }

  private static String composeMessage(final String MSG) {
    String remoteAddress = getClientIpAddr(request());
    String msgCalledFrom = String.format(MSG_CALLED_FROM_REMOTE_ADDRESS, remoteAddress) + "\n";
    return msgCalledFrom + MSG;
  }

	private static final List<String> IP_HEADERS = Arrays.asList("X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR");

	private static String getClientIpAddr(Http.Request request) {
		return IP_HEADERS.stream()
			.map(request::getHeader)
			.filter(Objects::nonNull)
			.filter(ip -> !ip.isEmpty() && !ip.equalsIgnoreCase("unknown"))
			.findFirst()
			.orElseGet(request::remoteAddress);
	}
}
