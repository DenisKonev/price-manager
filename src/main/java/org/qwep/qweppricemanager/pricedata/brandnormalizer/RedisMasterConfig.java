package org.qwep.qweppricemanager.pricedata.brandnormalizer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;


@Configuration
public class RedisMasterConfig {
    private final RedisMasterProps redisMasterProps;

    @Autowired
    public RedisMasterConfig(RedisMasterProps redisMasterProps) {
        this.redisMasterProps = redisMasterProps;
    }

    @Primary
    @Bean(name = "redisMasterConnectionFactory")
    public RedisConnectionFactory redisMasterConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(redisMasterProps.getHost(), redisMasterProps.getPort());
        redisStandaloneConfiguration.setPassword(RedisPassword.of(redisMasterProps.getPassword()));
        redisStandaloneConfiguration.setDatabase(redisMasterProps.getDatabase());
        return new JedisConnectionFactory(redisStandaloneConfiguration);
    }

    @Bean(name = "redis.master")
    public RedisTemplate<String, String> redisMasterTemplate(@Qualifier("redisMasterConnectionFactory") RedisConnectionFactory cf) {
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(cf);
        stringRedisTemplate.setDefaultSerializer(new StringRedisSerializer());
        return stringRedisTemplate;
    }
}
