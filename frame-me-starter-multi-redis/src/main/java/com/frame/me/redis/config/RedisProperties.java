package com.frame.me.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
         * 部署模式，默认单机.
         */
        private Mode mode = Mode.STANDALONE;

        /**
         * 主机地址（{@code STANDALONE} 模式）.
         */
        private String host = "localhost";

        /**
         * 端口（{@code STANDALONE} 模式）.
         */
        private int port = 6379;

        /**
         * 节点列表，格式 {@code host:port}（{@code CLUSTER} 集群节点 / {@code SENTINEL} 哨兵节点）.
         */
        private List<String> nodes = new ArrayList<>();

        /**
         * 哨兵监控的主节点名称（{@code SENTINEL} 模式）.
         */
        private String sentinelMaster;

        /**
         * 用户名（Redis 6.0+ ACL）.
         */
        private String username;

        /**
         * 密码.
         */
        private String password;

        /**
         * 数据库索引（{@code CLUSTER} 模式不支持，将被忽略）.
         */
        private int database = 0;
    }

    /**
     * Redis 部署模式.
     */
    public enum Mode {

        /**
         * 单机.
         */
        STANDALONE,

        /**
         * 集群.
         */
        CLUSTER,

        /**
         * 哨兵.
         */
        SENTINEL
    }
}
