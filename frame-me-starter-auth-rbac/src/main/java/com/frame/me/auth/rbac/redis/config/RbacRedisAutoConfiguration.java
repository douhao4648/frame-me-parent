package com.frame.me.auth.rbac.redis.config;

import com.frame.me.auth.rbac.config.RbacAutoConfiguration;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.redis.RedisAuthPermissionProvider;
import com.frame.me.auth.rbac.redis.store.PermissionCacheStore;
import com.frame.me.auth.rbac.redis.store.RedisPermissionCacheStore;
import com.frame.me.redis.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Redis 权限后端自动配置.
 *
 * <p>装配后，{@link RedisAuthPermissionProvider} 以 {@code @Primary} 成为生效的
 * {@link IAuthPermissionProvider}，包装数据源插槽 {@code authPermissionSource}
 * （由 {@link RbacAutoConfiguration} 提供，默认配置版实现；业务可声明同名 bean
 * 接入数据库等真实数据源）。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
// multi-redis 为 optional 依赖：RedisUtils 缺席时整个配置类在 ASM 元数据阶段跳过。
// 护栏：本条件必须保持在类级（类级 Class 字面量安全）；若改到方法级须换成 name 字符串形式，否则缺席场景会 NCDFE
@ConditionalOnClass(RedisUtils.class)
// 与 RbacAutoConfiguration 同走总开关：me.auth.permission.enabled=false 时本配置一并退避，不再空转装配
@ConditionalOnProperty(prefix = "me.auth.permission", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "me.auth.permission.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RbacRedisProperties.class)
// 必须在 RbacAutoConfiguration 之后处理：数据源插槽 authPermissionSource 由它注册；
// 若本配置先处理，下方的 @Primary 包装器（本身是 IAuthPermissionProvider）会使插槽的
// @ConditionalOnMissingBean(IAuthPermissionProvider) 误判退避，导致 @Qualifier("authPermissionSource") 注入失败
@AutoConfigureAfter(RbacAutoConfiguration.class)
public class RbacRedisAutoConfiguration {

    /**
     * 权限快照的 Redis 缓存存储.
     */
    @Bean
    @ConditionalOnMissingBean(PermissionCacheStore.class)
    public PermissionCacheStore permissionCacheStore(RbacRedisProperties properties) {
        return new RedisPermissionCacheStore(properties);
    }

    /**
     * 生效的权限提供者：Redis read-through 缓存，包装数据源插槽 {@code authPermissionSource}.
     */
    @Bean
    @Primary
    public IAuthPermissionProvider redisAuthPermissionProvider(
            @Qualifier("authPermissionSource") IAuthPermissionProvider source,
            RbacRedisProperties properties,
            PermissionCacheStore cacheStore) {
        return new RedisAuthPermissionProvider(source, properties, cacheStore);
    }
}
