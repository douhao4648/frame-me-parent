package com.frame.me.base.config;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域自动配置.
 *
 * <p>仅在 {@code me.cors.enabled=true} 时激活，注册最高优先级的 {@link CorsFilter}
 * （优先级高于 {@code AuthFilter} 的 {@code HIGHEST_PRECEDENCE+100}），
 * 在认证过滤器之前处理 OPTIONS 预检并附加 CORS 响应头。认证链仍对 OPTIONS 豁免作兜底。</p>
 *
 * <p>未启用时不注册任何 Bean，对不需要跨域的服务零侵入。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "me.cors", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CorsProperties.class)
public class CorsAutoConfiguration {

    private final CorsProperties properties;

    /**
     * 注册 CorsFilter：最高优先级，早于 AuthFilter 处理预检与 CORS 头。
     *
     * <p>用 FilterRegistrationBean 而非直接 @Bean CorsFilter，以便显式指定 order，
     * 确保 CORS 优先于认证过滤器。CorsFilter 内部对 OPTIONS 预检直接返回 CORS 头、
     * 不调用后续 FilterChain（handleInvalid=false 时不作为错误处理），从而认证链看不到预检请求。</p>
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildCorsConfiguration());

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        // 最高优先级：早于 AuthFilter(HIGHEST_PRECEDENCE+100)，确保 OPTIONS 预检先被 CORS 处理
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        log.info("CorsFilter registered: allowedOrigins={}, allowCredentials={}",
                properties.getAllowedOrigins(), properties.isAllowCredentials());
        return registration;
    }

    private CorsConfiguration buildCorsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        if (properties.getAllowedOrigins().isEmpty()) {
            // 默认放行所有来源
            config.addAllowedOriginPattern("*");
        } else {
            properties.getAllowedOrigins().forEach(config::addAllowedOriginPattern);
        }
        properties.getAllowedMethods().forEach(config::addAllowedMethod);
        properties.getAllowedHeaders().forEach(config::addAllowedHeader);
        properties.getExposedHeaders().forEach(config::addExposedHeader);
        config.setAllowCredentials(properties.isAllowCredentials());
        config.setMaxAge(properties.getMaxAge());
        return config;
    }
}
