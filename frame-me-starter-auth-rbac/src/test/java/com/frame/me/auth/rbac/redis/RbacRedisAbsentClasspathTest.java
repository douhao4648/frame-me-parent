package com.frame.me.auth.rbac.redis;

import com.frame.me.auth.rbac.config.RbacAutoConfiguration;
import com.frame.me.auth.rbac.permission.ConfigAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.redis.config.RbacRedisAutoConfiguration;
import com.frame.me.auth.rbac.redis.store.PermissionCacheStore;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisUtils 缺席时的装配测试.
 *
 * <p>multi-redis 是 auth-rbac 的 optional 依赖，消费方未引入时 {@link RbacRedisAutoConfiguration}
 * 必须在 ASM 元数据评估阶段整体退避（不加载 {@code RedisUtils}、不抛 NCDFE）,
 * RBAC 回退到 {@link RbacAutoConfiguration} 的配置版 provider。</p>
 *
 * <p>本模块 test classpath 恒有 multi-redis(optional 只阻断传递），故用
 * {@link FilteredClassLoader} 屏蔽 {@code com.frame.me.redis} 包来模拟缺席。</p>
 *
 * @author frame-me
 */
class RbacRedisAbsentClasspathTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RbacRedisAutoConfiguration.class, RbacAutoConfiguration.class))
            .withUserConfiguration(StubWriterConfig.class)
            .withClassLoader(new FilteredClassLoader("com.frame.me.redis"));

    /**
     * RedisUtils 缺席：redis 配置退避，配置版 provider 生效，零 redis bean.
     * 若上下文因 NCDFE 启动失败，以下 bean 断言会连带启动失败信息一起报出。
     */
    @Test
    void redisUtilsAbsent_redisAutoConfigBacksOff_configProviderActive() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(IAuthPermissionProvider.class);
            assertThat(context.getBean(IAuthPermissionProvider.class)).isInstanceOf(ConfigAuthPermissionProvider.class);
            assertThat(context).doesNotHaveBean(PermissionCacheStore.class);
            // 数据源插槽由始终在场的 RbacAutoConfiguration 注册，不受 RedisUtils 缺席影响
            assertThat(context).hasBean("authPermissionSource");
        });
    }

    /**
     * {@link RbacAutoConfiguration} 的 Filter/Interceptor bean 依赖 {@link IFilterErrorResponseWriter}
     * （由 starter-base 装配，本 runner 未加载），打桩使上下文可启动.
     */
    @Configuration(proxyBeanMethods = false)
    static class StubWriterConfig {
        @Bean
        IFilterErrorResponseWriter filterErrorResponseWriter() {
            return (response, resultCode, message) -> {
            };
        }
    }
}
