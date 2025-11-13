package org.qwep.qweppricemanager.mail.config.props;

import lombok.Data;
import lombok.Getter;
import org.qwep.qweppricemanager.mail.config.boilerplate.YamlPropertySourceFactory;
import org.qwep.qweppricemanager.mail.config.props.nested.MailProp;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "qwep")
@PropertySource(value = "classpath:imap-props.yaml", factory = YamlPropertySourceFactory.class)
@Data
@Getter
public class MailPropsConfig {

    @NestedConfigurationProperty
    private MailProp mail;

}

