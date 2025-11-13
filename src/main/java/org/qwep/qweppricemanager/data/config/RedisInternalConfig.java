package org.qwep.qweppricemanager.data.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisInternalConfig {
    private final RedisInternalProps redisInternalProps;

    @Autowired
    public RedisInternalConfig(RedisInternalProps redisInternalProps) {
        this.redisInternalProps = redisInternalProps;
    }

    @Bean(name = "redisInternalConnectionFactory")
    public RedisConnectionFactory redisInternalConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(redisInternalProps.getHost(), redisInternalProps.getPort());
        //uncomment if password will be used in future
        //redisStandaloneConfiguration.setPassword(RedisPassword.of(redisInternalProps.getPassword()));
        redisStandaloneConfiguration.setDatabase(redisInternalProps.getDatabase());
        return new JedisConnectionFactory(redisStandaloneConfiguration);
    }

    @Bean(name = "redis.internal")
    public RedisTemplate<String, String> redisInternalTemplate(@Qualifier("redisInternalConnectionFactory") RedisConnectionFactory cf) {
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(cf);
        stringRedisTemplate.setDefaultSerializer(new StringRedisSerializer());
        return stringRedisTemplate;
    }
}
