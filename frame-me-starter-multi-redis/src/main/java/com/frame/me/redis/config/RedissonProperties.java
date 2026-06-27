package com.frame.me.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redisson 配置属性.
 *
 * <p>绑定前缀 {@code spring.data.redis.redisson}，与 {@code redisson-spring-boot-starter} 的标准配置项对齐。</p>
 */
@Data
@ConfigurationProperties(prefix = "spring.data.redis.redisson")
public class RedissonProperties {

    /**
     * Redisson 原生配置文件位置（支持 {@code classpath:} / {@code file:} 前缀），可选.
     *
     * <p>配置后由 {@code Config.fromYAML} 解析，支持 single/cluster/sentinel/masterSlave/replicated 全部模式；
     * 留空则自动复用 {@code spring.data.redis.*}（single/cluster/sentinel）。</p>
     */
    private String config;

}
