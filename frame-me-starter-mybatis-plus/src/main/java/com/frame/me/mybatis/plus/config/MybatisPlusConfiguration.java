package com.frame.me.mybatis.plus.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.frame.me.base.util.SnowflakeUtils;
import com.frame.me.mybatis.plus.plugin.BaseMetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置.
 *
 * <p>当类路径存在 BaseMapper 时自动启用，注册分页插件、乐观锁插件、公共字段自动填充处理器以及
 * 可选的自定义雪花算法 ID 生成器。可通过 {@code me.mybatis.enabled=false} 关闭整个 starter，
 * 业务方自定义 {@link MybatisPlusInterceptor} Bean 时默认实现退避（@ConditionalOnMissingBean）.</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@ConditionalOnProperty(prefix = "me.mybatis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MybatisPlusProperties.class)
public class MybatisPlusConfiguration {

    /**
     * 注册 MyBatis-Plus 拦截器.
     *
     * <p>业务方自定义 {@link MybatisPlusInterceptor} Bean 时默认实现退避.
     * 分页插件的 {@link DbType} 由 {@code me.mybatis.db-type} 配置，默认 {@link DbType#MYSQL}.</p>
     *
     * @return MybatisPlusInterceptor
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(MybatisPlusProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件（DbType 可配置，适配不同数据库方言）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(properties.getDbType()));
        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 注册公共字段自动填充处理器.
     *
     * <p>默认不启用，需要通过配置 {@code me.mybatis.meta-object-handler.enabled=true} 开启。
     *
     * @return BaseMetaObjectHandler
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "me.mybatis",
            name = "meta-object-handler.enabled",
            havingValue = "true",
            matchIfMissing = false)
    public BaseMetaObjectHandler baseMetaObjectHandler() {
        return new BaseMetaObjectHandler();
    }

    /**
     * 注册雪花 ID 生成器.
     *
     * <p>当类路径存在 base 的 {@link SnowflakeUtils} 时默认生效，委托 base 统一雪花实例
     * （与 flex starter 共用同一套；节点 ID 由 {@code me.snowflake.worker-id} /
     * {@code me.snowflake.datacenter-id} 指定，未配置时 Hutool 依据 MAC + PID 自动推导）。
     * 显式配置 {@code me.snowflake.enabled=false} 或 base 不在类路径时本 Bean 退避，
     * 沿用 MyBatis-Plus 默认 ID 生成。
     *
     * @return IdentifierGenerator
     */
    @Bean
    @ConditionalOnMissingBean(IdentifierGenerator.class)
    @ConditionalOnClass(name = "com.frame.me.base.util.SnowflakeUtils")
    @ConditionalOnProperty(prefix = "me.snowflake", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IdentifierGenerator identifierGenerator() {
        log.info("MyBatis-Plus IdentifierGenerator delegated to base SnowflakeUtils");
        return entity -> SnowflakeUtils.nextId();
    }

}
