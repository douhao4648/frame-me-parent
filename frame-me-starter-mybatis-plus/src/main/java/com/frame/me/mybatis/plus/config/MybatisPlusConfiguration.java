package com.frame.me.mybatis.plus.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
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
     * 注册自定义雪花算法 ID 生成器.
     *
     * <p>当显式配置 {@code me.mybatis.snowflake.worker-id} 时生效，
     * 用于分布式环境下为每个实例分配唯一的 workerId / datacenterId。
     *
     * @param properties MyBatis-Plus 扩展配置属性
     * @return IdentifierGenerator
     */
    @Bean
    @ConditionalOnMissingBean(IdentifierGenerator.class)
    @ConditionalOnProperty(prefix = "me.mybatis.snowflake", name = "worker-id")
    public IdentifierGenerator identifierGenerator(MybatisPlusProperties properties) {
        long workerId = properties.getSnowflake().getWorkerId();
        long datacenterId = properties.getSnowflake().getDatacenterId() == null ? 0L : properties.getSnowflake().getDatacenterId();
        // MP 的 DefaultIdentifierGenerator 无单 workerId 构造器，datacenterId 未配置时默认 0
        // 多实例部署需显式配置 me.mybatis.snowflake.datacenter-id 避免雪花 ID 冲突
        if (properties.getSnowflake().getDatacenterId() == null) {
            log.warn("me.mybatis.snowflake.datacenter-id 未配置，默认 0。多实例部署需显式配置避免雪花 ID 冲突");
        }
        log.info("register custom Snowflake ID Generator：workerId={}, datacenterId={}", workerId, datacenterId);
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }

}
