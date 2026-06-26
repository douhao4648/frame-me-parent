package com.frame.me.cache.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * JetCache 基础设施自动配置.
 *
 * <p>JetCache 的核心基础设施（缓存管理器、连接、{@code jetcache.*} 配置）由官方
 * {@code jetcache-starter-redis-lettuce} 提供，本类不重复装配。</p>
 *
 * <p>方法级缓存能力（{@code @EnableMethodCache}）属于应用层注解，
 * 由业务工程在自己的启动类上声明，并按业务包名指定 {@code basePackages}，本 starter 不再写死扫描范围。</p>
 *
 * <p>本类仅负责注册 {@link JetCacheInfrastructureRoleFixer} 以消除 JetCache 内部配置类的
 * BeanPostProcessor 警告。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.alicp.jetcache.anno.config.EnableMethodCache")
@Import(JetCacheInfrastructureRoleFixer.class)
public class CacheAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("JetCache initialize Application starter use @EnableMethodCache");
    }

}
