package com.frame.me.mybatis.plus.config;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyBatis-Plus 扩展配置属性.
 *
 * <p>绑定前缀 {@code me.mybatis}，支持分页插件数据库类型与公共字段自动填充处理器的启停。
 * 雪花算法配置已统一至 base 的 {@code me.snowflake.*}。
 */
@Data
@ConfigurationProperties(prefix = "me.mybatis")
public class MybatisPlusProperties {

    /**
     * 分页插件数据库类型，默认 {@link DbType#MYSQL}.
     *
     * <p>不同数据库的分页 SQL 方言不同，业务方可按实际数据库配置
     * （如 {@code me.mybatis.db-type: postgresql}）.</p>
     */
    private DbType dbType = DbType.MYSQL;

    /**
     * 公共字段自动填充处理器配置.
     */
    private final MetaObjectHandlerProperties metaObjectHandler = new MetaObjectHandlerProperties();

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

}
