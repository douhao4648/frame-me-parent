package com.frame.me.base.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CorsAutoConfiguration} 测试.
 *
 * <p>CorsAutoConfiguration 带 {@code @ConditionalOnWebApplication(type=SERVLET)}，
 * 用 {@link WebApplicationContextRunner} 模拟 Servlet Web 环境；非 Web 应用装配
 * {@link CorsFilter} 会失败，该条件保证纯消息/定时任务服务零侵入.</p>
 *
 * @author frame-me
 */
class CorsAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(CorsAutoConfiguration.class);

    /**
     * 默认（me.cors.enabled 未设）不注册 CorsFilter，零侵入.
     */
    @Test
    void disabledByDefaultDoesNotRegisterCorsFilter() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CorsFilter.class);
            assertThat(context).doesNotHaveBean(CorsProperties.class);
        });
    }

    /**
     * 显式 enabled=false 也不注册.
     */
    @Test
    void explicitlyDisabledDoesNotRegister() {
        contextRunner.withPropertyValues("me.cors.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(CorsFilter.class);
        });
    }

    /**
     * enabled=true 时注册 FilterRegistrationBean（包装 CorsFilter）与 CorsProperties，
     * 默认 allowedOrigins 为空（放行所有来源）.
     */
    @Test
    @SuppressWarnings("rawtypes")
    void enabledRegistersCorsFilterWithDefaults() {
        contextRunner.withPropertyValues("me.cors.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context.getBean(FilterRegistrationBean.class).getFilter())
                    .isInstanceOf(CorsFilter.class);
            CorsProperties properties = context.getBean(CorsProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getAllowedOrigins()).isEmpty();
            assertThat(properties.isAllowCredentials()).isFalse();
            assertThat(properties.getMaxAge()).isEqualTo(3600L);
        });
    }

    /**
     * 显式配置 allowed-origins 后属性绑定生效.
     */
    @Test
    void allowedOriginsBoundFromConfig() {
        contextRunner.withPropertyValues(
                "me.cors.enabled=true",
                "me.cors.allowed-origins[0]=https://example.com",
                "me.cors.allowed-origins[1]=https://app.example.com",
                "me.cors.allow-credentials=true",
                "me.cors.max-age=7200"
        ).run(context -> {
            CorsProperties properties = context.getBean(CorsProperties.class);
            assertThat(properties.getAllowedOrigins())
                    .containsExactly("https://example.com", "https://app.example.com");
            assertThat(properties.isAllowCredentials()).isTrue();
            assertThat(properties.getMaxAge()).isEqualTo(7200L);
        });
    }
}
