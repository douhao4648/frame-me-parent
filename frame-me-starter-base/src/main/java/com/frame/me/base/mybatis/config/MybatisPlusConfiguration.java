package com.frame.me.base.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.frame.me.base.mybatis.plugin.BaseMetaObjectHandler;
import com.frame.me.base.mybatis.util.SnowflakeUtils;
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
 * 可选的自定义雪花算法 ID 生成器。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@EnableConfigurationProperties(MybatisPlusProperties.class)
public class MybatisPlusConfiguration {

    /**
     * 注册 MyBatis-Plus 拦截器.
     *
     * @return MybatisPlusInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 注册公共字段自动填充处理器.
     *
     * <p>默认不启用，需要通过配置 {@code frame.me.mybatis.meta-object-handler.enabled=true} 开启。
     *
     * @return BaseMetaObjectHandler
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "frame.me.mybatis",
            name = "meta-object-handler.enabled",
            havingValue = "true",
            matchIfMissing = false)
    public BaseMetaObjectHandler baseMetaObjectHandler() {
        return new BaseMetaObjectHandler();
    }

    /**
     * 注册自定义雪花算法 ID 生成器.
     *
     * <p>当显式配置 {@code frame.me.mybatis.snowflake.worker-id} 时生效，
     * 用于分布式环境下为每个实例分配唯一的 workerId / datacenterId。
     *
     * @param properties MyBatis-Plus 扩展配置属性
     * @return IdentifierGenerator
     */
    @Bean
    @ConditionalOnMissingBean(IdentifierGenerator.class)
    @ConditionalOnProperty(prefix = "frame.me.mybatis.snowflake", name = "worker-id")
    public IdentifierGenerator identifierGenerator(MybatisPlusProperties properties) {
        long workerId = properties.getSnowflake().getWorkerId();
        long datacenterId = properties.getSnowflake().getDatacenterId();
        log.info("register custom Snowflake ID Generator：workerId={}, datacenterId={}", workerId, datacenterId);
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }

    /**
     * 注册雪花 ID 生成工具类.
     *
     * <p>供业务代码手动生成雪花 ID，优先复用容器中自定义的 {@link IdentifierGenerator}，
     * 否则回退到 MyBatis-Plus 默认实现。
     *
     * @return SnowflakeUtils
     */
    @Bean
    @ConditionalOnMissingBean(SnowflakeUtils.class)
    public SnowflakeUtils snowflakeUtils() {
        return new SnowflakeUtils();
    }

}
