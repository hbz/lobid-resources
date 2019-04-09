/* Copyright 2019 hbz, Pascal Christoph. Licensed under the EPL 2.0*/
package de.hbz.lobid.helper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 * Sends an email via localhost smtp. Make sure the server is configured
 * properly.
 * 
 * @author Pascal Christoph (dr0i)
 *
 */
public class Email {

	/**
	 * Sends an email.
	 * 
	 * @author Pascal Christoph (dr0i)
	 * 
	 * @param FROM_NAME the name before the "@" of an email
	 * @param TO_EMAIL the complete email address of the receiver
	 * @param SUBJECT the subject of the mail
	 * @param MESSAGE the plain text message of the mail
	 */
	static public void sendEmail(final String FROM_NAME, final String TO_EMAIL,
			final String SUBJECT, final String MESSAGE) {
		Properties prop = new Properties();
		prop.put("mail.smtp.auth", false);
		prop.put("mail.smtp.starttls.enable", "false");
		prop.put("mail.smtp.host", "127.0.0.1");
		prop.put("mail.smtp.port", "25");

		Session session = Session.getInstance(prop);

		Message message = new MimeMessage(session);
		try {
			message.setFrom(new InternetAddress(
					FROM_NAME + "@" + InetAddress.getLocalHost().getHostName()));
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(TO_EMAIL));
			message.setSubject(SUBJECT);

			MimeBodyPart mimeBodyPart = new MimeBodyPart();
			mimeBodyPart.setContent(MESSAGE, "text/plain");
			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(mimeBodyPart);

			message.setContent(multipart);

			Transport.send(message);
		} catch (MessagingException | UnknownHostException e) {
			e.printStackTrace();
		}
	}

}
