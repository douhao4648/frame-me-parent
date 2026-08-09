package com.frame.me.base.config;

import com.frame.me.base.advice.GlobalExceptionHandler;
import com.frame.me.base.env.EnvironmentHelper;
import com.frame.me.base.result.ResultJacksonModule;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import com.frame.me.base.web.ResultFilterErrorResponseWriter;
import com.frame.me.validation.validator.TimeRangeValidator;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

/**
 * frame-me-starter-base 自动配置.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({ExceptionProperties.class, SecurityHeadersProperties.class})
public class BaseAutoConfiguration {

    /**
     * 值非空白时才设置响应头，空字符串表示业务方主动关闭该头.
     */
    private static void setHeaderIfNotBlank(HttpServletResponse res, String name, String value) {
        if (value != null && !value.isBlank()) {
            res.setHeader(name, value);
        }
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler(ExceptionProperties exceptionProperties) {
        return new GlobalExceptionHandler(exceptionProperties);
    }

    @Bean
    public EnvironmentHelper environmentHelper(Environment environment) {
        return new EnvironmentHelper(environment);
    }

    @Bean
    @ConditionalOnMissingBean(ResultJacksonModule.class)
    ResultJacksonModule resultJacksonModule() {
        return new ResultJacksonModule();
    }

    /**
     * 安全响应头 Filter：添加基础浏览器安全头，防 XSS/clickjacking/MIME-sniffing.
     *
     * <p>各项头部值均可通过 {@link SecurityHeadersProperties}（{@code me.security.headers.*}）调整；
     * 空字符串表示不设置对应头。HSTS 默认关闭，由业务在 HTTPS 环境显式开启
     * （{@code me.security.headers.hsts-enabled=true}），避免开发环境自签证书走 HTTPS
     * 时被浏览器长期锁定；若网关/反向代理已统一处理 HSTS，此处保持默认关闭即可.</p>
     */
    @Bean
    public FilterRegistrationBean<Filter> securityHeadersFilter(SecurityHeadersProperties securityHeadersProperties) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((servletRequest, servletResponse, chain) -> {
            HttpServletResponse res = (HttpServletResponse) servletResponse;
            setHeaderIfNotBlank(res, "X-Content-Type-Options", securityHeadersProperties.getXContentTypeOptions());
            setHeaderIfNotBlank(res, "X-Frame-Options", securityHeadersProperties.getXFrameOptions());
            setHeaderIfNotBlank(res, "Referrer-Policy", securityHeadersProperties.getReferrerPolicy());
            setHeaderIfNotBlank(res, "Permissions-Policy", securityHeadersProperties.getPermissionsPolicy());
            if (securityHeadersProperties.isCspEnabled()) {
                setHeaderIfNotBlank(res, "Content-Security-Policy", securityHeadersProperties.getCsp());
            }
            if (securityHeadersProperties.isHstsEnabled()) {
                setHeaderIfNotBlank(res, "Strict-Transport-Security", securityHeadersProperties.getHsts());
            }
            chain.doFilter(servletRequest, servletResponse);
        });
        // ponytail: CorsFilter 也是 HIGHEST_PRECEDENCE，安全头 filter 比它晚一个位置，
        // 确保 CorsFilter 对 OPTIONS 预检短路后安全头仍能由 CorsFilter 自身 headers 覆盖
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * 默认 Filter 层错误响应写入器：输出 {@link com.frame.me.base.result.Result} 格式.
     */
    @Bean
    @ConditionalOnMissingBean(IFilterErrorResponseWriter.class)
    public IFilterErrorResponseWriter filterErrorResponseWriter(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new ResultFilterErrorResponseWriter(objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    /**
     * 应用上下文关闭时清理 {@link com.frame.me.validation.validator.TimeRangeValidator} 静态缓存，
     * 释放旧 ClassLoader 引用，避免 devtools 热重启等场景下 ClassLoader 泄漏.
     */
    @EventListener
    public void onContextClosed(ContextClosedEvent event) {
        TimeRangeValidator.cleanup();
    }

}
