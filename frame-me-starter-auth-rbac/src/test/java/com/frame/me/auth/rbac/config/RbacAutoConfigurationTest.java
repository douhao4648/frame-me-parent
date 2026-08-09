package com.frame.me.auth.rbac.config;

import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RbacAutoConfiguration} 装配测试：验证默认装配与 Web 应用类型条件.
 *
 * @author frame-me
 */
class RbacAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RbacAutoConfiguration.class))
            .withUserConfiguration(StubWriterConfig.class);

    /**
     * Servlet Web 应用：配置版权限数据源插槽、权限过滤器、权限拦截器正常装配.
     */
    @Test
    void assemblesInServletWebApplication() {
        webRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("authPermissionSource");
            assertThat(context).hasSingleBean(IAuthPermissionProvider.class);
            assertThat(context).hasBean("permissionFilter");
            assertThat(context).hasBean("permissionInterceptorConfigurer");
        });
    }

    /**
     * 父开关级联：me.auth.enabled=false 时本模块整体退避（总闸语义）.
     */
    @Test
    void masterSwitchDisabled_backsOff() {
        webRunner.withPropertyValues("me.auth.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("authPermissionSource");
                    assertThat(context).doesNotHaveBean("permissionFilter");
                    assertThat(context).doesNotHaveBean("permissionInterceptorConfigurer");
                });
    }

    /**
     * 非 Servlet Web 应用整体退避：权限校验是 Web 请求关注点，Filter/拦截器无挂载点.
     */
    @Test
    void backsOffInNonWebApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RbacAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("authPermissionSource");
                    assertThat(context).doesNotHaveBean("permissionFilter");
                });
    }

    /**
     * 测试基础设施：PermissionFilter 依赖的错误响应写入器 stub.
     */
    @Configuration(proxyBeanMethods = false)
    static class StubWriterConfig {

        @Bean
        IFilterErrorResponseWriter filterErrorResponseWriter() {
            return (HttpServletResponse response, ResultCode resultCode, String message) -> {
            };
        }
    }
}
