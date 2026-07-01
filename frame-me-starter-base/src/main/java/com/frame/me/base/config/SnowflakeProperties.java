package com.frame.me.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 雪花 ID 配置属性.
 *
 * <p>绑定前缀 {@code me.snowflake}。仅在分布式多副本部署需为每个实例分配唯一节点标识时配置；
 * 不配置则由 Hutool 依据主机 MAC + PID 自动推导。</p>
 */
@Data
@ConfigurationProperties(prefix = "me.snowflake")
public class SnowflakeProperties {

    /**
     * 工作机器 ID（0~31）.未配置（null）时由 Hutool 依据 MAC + PID 自动推导。
     */
    private Long workerId;

    /**
     * 数据中心 ID（0~31）.未配置（null）时由 Hutool 依据 MAC 自动推导。
     */
    private Long datacenterId;
}
