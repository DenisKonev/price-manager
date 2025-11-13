package org.qwep.qweppricemanager.external;

import lombok.Data;
import lombok.Getter;
import org.qwep.qweppricemanager.mail.config.boilerplate.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "api-qwep-query")
@PropertySource(value = "classpath:api-qwep-queries.yaml", factory = YamlPropertySourceFactory.class)
@Data
@Getter
public class ApiQwepQueriesConfig {
    private String vendorIdByPriceCodeQuery;
    private String vendorIdByEmailQuery;
    private String vendorIdsByQuserToken;
    private String vendorSecurityType;
    private String vendorCoreName;
}
