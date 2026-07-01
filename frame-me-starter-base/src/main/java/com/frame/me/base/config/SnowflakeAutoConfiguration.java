package com.frame.me.base.config;

import cn.hutool.core.util.IdUtil;
import com.frame.me.base.util.SnowflakeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花 ID 自动配置.
 *
 * <p>仅当显式配置 {@code me.snowflake.worker-id} 时生效，用于分布式多副本环境下为每个实例
 * 指定唯一的 workerId / datacenterId，覆盖 {@link SnowflakeUtils} 默认的自动推导实例。
 * 其中未显式配置的字段仍回退到 Hutool 自动推导。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SnowflakeProperties.class)
@ConditionalOnProperty(prefix = "me.snowflake", name = "worker-id")
public class SnowflakeAutoConfiguration {

    /**
     * Snowflake workerId / datacenterId 的最大值（各 5 位，0~31）.
     */
    private static final long MAX_ID = 31L;

    public SnowflakeAutoConfiguration(SnowflakeProperties properties) {
        long datacenterId = properties.getDatacenterId() != null ? properties.getDatacenterId() : IdUtil.getDataCenterId(MAX_ID);
        long workerId = properties.getWorkerId() != null ? properties.getWorkerId() : IdUtil.getWorkerId(datacenterId, MAX_ID);
        SnowflakeUtils.configure(workerId, datacenterId);
        log.info("configure Snowflake: workerId={}, datacenterId={}", workerId, datacenterId);
    }

}
