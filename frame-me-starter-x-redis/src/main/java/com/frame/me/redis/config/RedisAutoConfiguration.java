package com.frame.me.redis.config;

import com.frame.me.redis.util.RedisUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 自动配置.
 *
 * <p>启用后注册 {@link RedisUtils} 所需的 RedisTemplate 引用。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "frame.me.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedisProperties.class)
public class RedisAutoConfiguration {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<Object, Object> redisTemplate;


    @Autowired(required = false)
    public RedisAutoConfiguration(StringRedisTemplate stringRedisTemplate, RedisTemplate<Object, Object> redisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        RedisUtils.init(stringRedisTemplate, redisTemplate);
        log.info("Redis 基础能力已启用");
    }

}
