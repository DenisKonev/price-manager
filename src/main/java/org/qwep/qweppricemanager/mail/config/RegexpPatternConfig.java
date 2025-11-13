package org.qwep.qweppricemanager.mail.config;

import lombok.Data;
import lombok.Getter;
import org.qwep.qweppricemanager.mail.config.boilerplate.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "qwep-regexp")
@PropertySource(value = "classpath:regexps.yaml", factory = YamlPropertySourceFactory.class)
@Data
@Getter
public class RegexpPatternConfig {
    private String emailRegexp;
}
