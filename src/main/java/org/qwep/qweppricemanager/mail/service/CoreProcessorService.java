package org.qwep.qweppricemanager.mail.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.commons.UserTextsConfig;
import org.qwep.qweppricemanager.mail.config.props.MailPropsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@Slf4j
public class CoreProcessorService {

    private final JavaMailSender mailSender;
    private final MailPropsConfig mailProps;

    private final UserTextsConfig userTextsConfig;

    @Autowired
    public CoreProcessorService(
            JavaMailSender mailSender,
            MailPropsConfig mailProps,
            UserTextsConfig userTextsConfig) {
        this.mailSender = mailSender;
        this.mailProps = mailProps;
        this.userTextsConfig = userTextsConfig;
    }


    public void sendPriceLifecycleOverWarningToClient(String email, String adminCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProps.getMail().getYandex().getUser());
        message.setText(String.format(userTextsConfig.getPriceLifecycleOverWarning(), adminCode));
        message.setSentDate(new Date());
        message.setSubject(userTextsConfig.getEmailTitle());
        message.setReplyTo(email);
        message.setTo(email);
        mailSender.send(message);
        log.info("successfully sent price update request notification to client: {}", email);
    }

    public void sendPriceDropNotificationToClient(String email, String adminCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProps.getMail().getYandex().getUser());
        message.setText(String.format(userTextsConfig.getPriceDropNotification(), adminCode));
        message.setSentDate(new Date());
        message.setSubject(userTextsConfig.getEmailTitle());
        message.setReplyTo(email);
        message.setTo(email);
        mailSender.send(message);
        log.info("successfully sent price drop notification to client: {}", email);
    }


    public JavaMailSender getMailSender() {
        return this.mailSender;
    }


    public void sendMessageToClient(String email, String messageText) throws MessagingException {
        MimeMessage message = getMailSender().createMimeMessage();
        InternetAddress address = new InternetAddress(email);
        message.setFrom(new InternetAddress(mailProps.getMail().getYandex().getUser()));
        message.setContent(messageText, "text/html;charset=UTF-8");
        message.setSentDate(new Date());
        message.setSubject("Сообщение от QWEP");
        message.setReplyTo(new InternetAddress[]{address});
        message.setRecipients(Message.RecipientType.TO, new InternetAddress[]{address});
        mailSender.send(message);
        log.info("successfully sent basket add item message to client: {}", email);
    }
}
