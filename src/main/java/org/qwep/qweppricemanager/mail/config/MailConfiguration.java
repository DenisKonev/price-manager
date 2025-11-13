package org.qwep.qweppricemanager.mail.config;

import jakarta.mail.*;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.input.SAXBuilder;
import org.qwep.qweppricemanager.mail.config.interfaces.IMailProvider;
import org.qwep.qweppricemanager.mail.config.props.MailPropsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Slf4j
@Configuration
public class MailConfiguration {

    private final MailPropsConfig mailProps;

    @Autowired
    public MailConfiguration(MailPropsConfig mailProps) {
        this.mailProps = mailProps;
    }

    @Bean
    public SAXBuilder saxBuilder() {
        return new SAXBuilder();
    }

    @Bean
    public Store yandexMailStore() throws MessagingException {

        IMailProvider provider = mailProps.getMail().getYandex();
        Properties props = new Properties();
        props.setProperty("mail.user", provider.getUser());
        props.setProperty("mail.password", provider.getPassword());
        props.setProperty("mail.host", provider.getHost());
        props.setProperty("mail.store.protocol", provider.getProtocol());

        Store store;
        store = Session.getInstance(props).getStore();
        store.connect(provider.getHost(), provider.getUser(), provider.getPassword());
        log.info("successfully connected to mailbox");
        return store;

    }

    @Bean
    public JavaMailSender mailSenderSession() {

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailProps.getMail().getYandex().getSmtp().getHost());
        mailSender.setPort(Integer.parseInt(mailProps.getMail().getYandex().getSmtp().getPort()));
        mailSender.setUsername(mailProps.getMail().getYandex().getUser());
        mailSender.setPassword(mailProps.getMail().getYandex().getPassword());
        mailSender.setDefaultEncoding("UTF-8");
        mailSender.setProtocol(mailProps.getMail().getYandex().getSmtp().getProtocol());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.starttls.enable", true);
        props.put("mail.smtp.host", mailProps.getMail().getYandex().getSmtp().getHost());
        props.put("mail.smtp.port", Integer.parseInt(mailProps.getMail().getYandex().getSmtp().getPort()));
        props.put("mail.smtp.ssl.trust", mailProps.getMail().getYandex().getSmtp().getHost());

        mailSender.setSession(Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                        mailProps.getMail().getYandex().getUser(),
                        mailProps.getMail().getYandex().getPassword()
                );
            }
        }));

        return mailSender;
    }
}
