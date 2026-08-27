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
     * 是否启用 ORM starter（MyBatis-Plus / MyBatis-Flex）的雪花生成器委托，默认 true.
     *
     * <p>启用时 plus / flex 的主键生成统一委托 base 的 SnowflakeUtils（同一套雪花实例）；
     * 显式设为 false 可退回各 ORM 自带的 ID 生成器。</p>
     */
    private boolean enabled = true;

    /**
     * 工作机器 ID（0~31）.未配置（null）时由 Hutool 依据 MAC + PID 自动推导。
     */
    private Long workerId;

    /**
     * 数据中心 ID（0~31）.未配置（null）时由 Hutool 依据 MAC 自动推导。
     */
    private Long datacenterId;
}
