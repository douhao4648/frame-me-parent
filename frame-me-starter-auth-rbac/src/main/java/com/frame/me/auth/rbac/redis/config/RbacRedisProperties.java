package com.frame.me.auth.rbac.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis 权限中心配置属性.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.auth.permission.redis")
public class RbacRedisProperties {

    /**
     * 是否启用 Redis 权限中心，默认启用.
     */
    private Boolean enabled = true;

    /**
     * 权限快照在 Redis 中的 key 前缀.
     */
    private String keyPrefix = "auth:perms:";

    /**
     * Redis 实例名（对应 {@code me.redis.clients} 或默认 {@code default}）.
     */
    private String clientName = "default";

    /**
     * 权限快照在 Redis 中的过期时间，默认 30 分钟.
     */
    private Duration redisTtl = Duration.ofMinutes(30);

    /**
     * L1 本地缓存过期时间，默认 5 秒.
     */
    private Duration localTtl = Duration.ofSeconds(5);

    /**
     * L1 本地缓存最大容量，默认 10000.
     */
    private long localMaxSize = 10000L;
}
