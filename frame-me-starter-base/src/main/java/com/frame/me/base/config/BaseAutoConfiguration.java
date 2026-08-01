package com.frame.me.base.config;

import tools.jackson.databind.ObjectMapper;
import com.frame.me.base.advice.GlobalExceptionHandler;
import com.frame.me.base.env.EnvironmentHelper;
import com.frame.me.base.result.ResultJacksonModule;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import com.frame.me.base.web.ResultFilterErrorResponseWriter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.io.IOException;

/**
 * frame-me-starter-base 自动配置.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExceptionProperties.class)
public class BaseAutoConfiguration {

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
     * <p>HSTS（Strict-Transport-Security）不在此设置，应交由网关/反向代理（TLS 终结点）统一处理，
     * 避免应用层因开发环境无 HTTPS 导致浏览器永久拒绝 HTTP 连接。</p>
     */
    @Bean
    public FilterRegistrationBean<Filter> securityHeadersFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((servletRequest, servletResponse, chain) -> {
            HttpServletResponse res = (HttpServletResponse) servletResponse;
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setHeader("X-Frame-Options", "DENY");
            res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            res.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
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
        com.frame.me.validation.validator.TimeRangeValidator.cleanup();
    }

}
