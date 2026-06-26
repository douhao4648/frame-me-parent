package com.frame.me.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 模块配置属性.
 *
 * <p>绑定前缀 {@code frame.me.redis}，控制 Redis 自动配置的启停。</p>
 */
@Data
@ConfigurationProperties(prefix = "frame.me.redis")
public class RedisProperties {

    /**
     * 是否启用 Redis 自动配置，默认关闭.
     */
    private boolean enabled = true;

}
