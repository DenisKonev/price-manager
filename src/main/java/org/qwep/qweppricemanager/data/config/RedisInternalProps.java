package org.qwep.qweppricemanager.data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.redis2")
public class RedisInternalProps extends RedisCommonProps {
}
