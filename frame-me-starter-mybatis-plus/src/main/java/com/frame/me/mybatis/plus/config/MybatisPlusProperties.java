package com.frame.me.mybatis.plus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyBatis-Plus 扩展配置属性.
 *
 * <p>绑定前缀 {@code me.mybatis}，支持显式配置雪花算法的 workerId / datacenterId
 * 以及公共字段自动填充处理器的启停。
 */
@Data
@ConfigurationProperties(prefix = "me.mybatis")
public class MybatisPlusProperties {

    /**
     * 公共字段自动填充处理器配置.
     */
    private final MetaObjectHandlerProperties metaObjectHandler = new MetaObjectHandlerProperties();

    /**
     * 雪花算法 ID 生成器配置.
     */
    private final SnowflakeProperties snowflake = new SnowflakeProperties();

    /**
     * 公共字段自动填充处理器配置.
     */
    @Data
    public static class MetaObjectHandlerProperties {

        /**
         * 是否启用公共字段自动填充处理器，默认关闭.
         */
        private boolean enabled = false;
    }

    /**
     * 雪花算法 ID 生成器配置.
     */
    @Data
    public static class SnowflakeProperties {

        /**
         * 工作机器 ID，范围 0~31.
         * <p>未配置时使用 MyBatis-Plus 默认推导值。
         */
        private Long workerId;

        /**
         * 数据中心 ID，范围 0~31，默认 0.
         * <p>未配置时使用 MyBatis-Plus 默认推导值。
         */
        private Long datacenterId = 0L;
    }
}
