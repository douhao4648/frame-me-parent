package com.frame.me.auth.rbac.redis;

import com.frame.me.auth.rbac.config.RbacAutoConfiguration;
import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.auth.rbac.permission.ConfigAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.redis.config.RbacRedisAutoConfiguration;
import com.frame.me.auth.rbac.redis.store.IPermissionCacheStore;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 权限后端装配测试：验证插槽 + {@code @Primary} 包装器装配与边界组合.
 *
 * <p>数据源插槽 {@code authPermissionSource} 由 {@link RbacAutoConfiguration} 提供，
 * 故 runner 同时加载两个自动配置。装配无需真实 Redis：{@code RedisAuthPermissionProvider}
 * 与 {@code ConfigAuthPermissionProvider} 构造时均不建立连接，可纯内存验证 bean 装配关系。</p>
 *
 * @author frame-me
 */
class RbacRedisAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RbacAutoConfiguration.class, RbacRedisAutoConfiguration.class))
            .withUserConfiguration(StubWriterConfig.class);

    /**
     * 默认装配：{@code authPermissionSource} 为配置版，按类型注入生效的是 {@code @Primary} 的 Redis 包装器.
     */
    @Test
    void defaultWiring_redisProviderIsPrimary_andConfigSourceDelegate() {
        runner.run(context -> {
            assertThat(context).hasBean("authPermissionSource");
            assertThat(context.getBean("authPermissionSource")).isInstanceOf(ConfigAuthPermissionProvider.class);
            assertThat(context.getBean(IAuthPermissionProvider.class)).isInstanceOf(RedisAuthPermissionProvider.class);
            assertThat(context).hasSingleBean(RbacProperties.class);
        });
    }

    /**
     * 业务声明名为 {@code authPermissionSource} 的 bean 时，默认委托数据源退避，Redis 仍以其为真实数据源.
     */
    @Test
    void businessAuthPermissionSource_overridesDefaultDelegate() {
        runner.withUserConfiguration(BusinessSourceConfig.class)
                .run(context -> {
                    assertThat(context.getBean("authPermissionSource")).isInstanceOf(BusinessSource.class);
                    assertThat(context.getBean(IAuthPermissionProvider.class)).isInstanceOf(RedisAuthPermissionProvider.class);
                });
    }

    /**
     * redis 模式下业务声明未命名的 provider：默认插槽按类型退避后包装器按名注入失败，
     * 启动 fail-fast（而非静默忽略业务 provider）.
     */
    @Test
    void unnamedBusinessProvider_redisMode_failsFast() {
        runner.withUserConfiguration(UnnamedSourceConfig.class)
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    /**
     * 总开关关闭时两个配置一并退避：{@code me.auth.permission.enabled=false} 时不装配任何 bean，
     * 避免权限校验已关闭还空转创建缓存 provider。
     */
    @Test
    void rbacDisabled_redisAutoConfigBacksOff() {
        runner.withPropertyValues("me.auth.permission.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IAuthPermissionProvider.class);
                    assertThat(context).doesNotHaveBean("authPermissionSource");
                    assertThat(context).doesNotHaveBean(IPermissionCacheStore.class);
                });
    }

    /**
     * redis 开关单独关闭时本配置退避：{@code me.auth.permission.redis.enabled=false} 后
     * RBAC 回退到插槽默认的配置版 provider（不加缓存层）.
     */
    @Test
    void redisDisabled_redisAutoConfigBacksOff() {
        runner.withPropertyValues("me.auth.permission.redis.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(IAuthPermissionProvider.class);
                    assertThat(context.getBean(IAuthPermissionProvider.class)).isInstanceOf(ConfigAuthPermissionProvider.class);
                    assertThat(context).doesNotHaveBean(IPermissionCacheStore.class);
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

    /**
     * 业务自定义数据源（命名为 {@code authPermissionSource}）.
     */
    @Configuration(proxyBeanMethods = false)
    static class BusinessSourceConfig {
        @Bean(name = "authPermissionSource")
        IAuthPermissionProvider businessSource() {
            return new BusinessSource();
        }
    }

    /**
     * 业务自定义 provider（未命名为 {@code authPermissionSource}）.
     */
    @Configuration(proxyBeanMethods = false)
    static class UnnamedSourceConfig {
        @Bean
        IAuthPermissionProvider myPermissionProvider() {
            return new BusinessSource();
        }
    }

    /**
     * 空实现，仅用于区分 bean 类型（接口方法均为 default）.
     */
    static class BusinessSource implements IAuthPermissionProvider {
    }
}
