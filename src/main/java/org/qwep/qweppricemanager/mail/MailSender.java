package org.qwep.qweppricemanager.mail;

import lombok.extern.slf4j.Slf4j;
import org.qwep.qweppricemanager.commons.UserTextsConfig;
import org.qwep.qweppricemanager.mail.config.props.MailPropsConfig;
import org.qwep.qweppricemanager.pricedata.Summary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class MailSender {
    private final MailPropsConfig mailProps;
    private final JavaMailSender javaMailSender;
    private final UserTextsConfig userTextsConfig;

    public MailSender(MailPropsConfig mailProps,
                      JavaMailSender javaMailSender,
                      UserTextsConfig userTextsConfig) {
        this.mailProps = mailProps;
        this.javaMailSender = javaMailSender;
        this.userTextsConfig = userTextsConfig;
    }

    private void sendMessage(String email, String message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(mailProps.getMail().getYandex().getUser());
        mailMessage.setText(message);
        mailMessage.setSentDate(new Date());
        mailMessage.setSubject(userTextsConfig.getEmailTitle());
        mailMessage.setReplyTo(mailProps.getMail().getYandex().getUser());
        mailMessage.setTo(email);
        javaMailSender.send(mailMessage);
        log.info("successfully sent price mail to client: {}", email);
    }

    public void sendPriceSummary(String email, String fimeName, Summary summary, String adminCode) {
        String message = String.format(userTextsConfig.getPriceLoadingResultsSummary(),
                adminCode, fimeName, summary.getLoadedDataRowsCount(), summary.getDeclinedDataRowsCount());
        sendMessage(email, message);
        log.info("Sent price summary to email: {} adminCode{}", email, adminCode);
    }

    public void sendErrorMessage(String email, String adminCode) {
        String message = String.format(userTextsConfig.getPriceRemoveWrongMessages(), adminCode);
        sendMessage(email, message);
        log.info("Sent error message to email: {} adminCode{}", email, adminCode);
    }
}
