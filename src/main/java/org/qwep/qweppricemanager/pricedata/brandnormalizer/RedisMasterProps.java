package org.qwep.qweppricemanager.pricedata.brandnormalizer;

import org.qwep.qweppricemanager.data.config.RedisCommonProps;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.redis")
public class RedisMasterProps extends RedisCommonProps {
}
