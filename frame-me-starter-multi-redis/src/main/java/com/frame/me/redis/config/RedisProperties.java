package com.frame.me.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis 模块配置属性.
 *
 * <p>绑定前缀 {@code frame.me.redis}，控制 Redis 自动配置的启停，并支持多实例配置。</p>
 */
@Data
@ConfigurationProperties(prefix = "frame.me.redis")
public class RedisProperties {

    /**
     * 是否启用 Redis 自动配置，默认开启.
     */
    private boolean enabled = true;

    /**
     * 多 Redis 实例配置，key 为实例名。
     *
     * <p>默认实例仍由 {@code spring.data.redis.*} 提供，名称为 {@code default}；
     * 此处配置的是额外实例，可通过 {@code RedisUtils.getClient(name)} 获取。</p>
     */
    private Map<String, ClientConfig> clients = new HashMap<>();

    /**
     * 单个 Redis 实例配置.
     */
    @Data
    public static class ClientConfig {

        /**
         * 主机地址.
         */
        private String host = "localhost";

        /**
         * 端口.
         */
        private int port = 6379;

        /**
         * 用户名（Redis 6.0+ ACL）.
         */
        private String username;

        /**
         * 密码.
         */
        private String password;

        /**
         * 数据库索引.
         */
        private int database = 0;
    }
}
