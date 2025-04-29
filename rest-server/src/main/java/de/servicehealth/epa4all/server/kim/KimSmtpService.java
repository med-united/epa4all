package de.servicehealth.epa4all.server.kim;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Properties;
import java.util.UUID;

@ApplicationScoped
public class KimSmtpService {

    private static final Logger log = LoggerFactory.getLogger(KimSmtpService.class.getName());

    @Inject
    SmtpConfig smtpConfig;

    @Inject
    KimConfig kimConfig;

    public String sendERezeptToKIMAddress(String prescription, String noteToPharmacy) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpConfig.getServer());
            props.put("mail.smtp.port", smtpConfig.getPort());
            props.put("mail.smtp.auth", smtpConfig.isAuth());
            props.put("mail.smtp.starttls.enable", smtpConfig.isStarttls());
            props.put("mail.smtp.authentication", smtpConfig.getAuthentication());

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpConfig.getUser(), smtpConfig.getPassword());
                }
            });
            MimeMessage msg = new MimeMessage(session);
            msg.addHeader("X-KIM-Dienstkennung", kimConfig.getDienstkennungHeader());
            msg.addHeader("X-KIM-Encounter-Id", UUID.randomUUID().toString());

            String fromKimAddress = kimConfig.getFromAddress();
            msg.setFrom(new InternetAddress(fromKimAddress));
            msg.setReplyTo(InternetAddress.parse(fromKimAddress, false));
            msg.setSubject(kimConfig.getSubject(), "UTF-8");

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText((noteToPharmacy == null ? "Hello" : noteToPharmacy) + "\r\n\r\n\r\n", "utf-8");

            MimeBodyPart erezeptTokenPart = new MimeBodyPart();
            erezeptTokenPart.setText(prescription, "utf8");

            Multipart multiPart = new MimeMultipart();
            multiPart.addBodyPart(textPart); // <-- first
            multiPart.addBodyPart(erezeptTokenPart); // <-- second
            msg.setContent(multiPart);

            msg.setSentDate(new Date());

            String toKimAddress = kimConfig.getToAddress();
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toKimAddress, false));
            Transport.send(msg);

            log.info("E-Mail sent successfully to: " + toKimAddress);
            return "OK";
        } catch (Exception e) {
            log.error("Error during sending E-Prescription", e);
            return e.getMessage();
        }
    }
}