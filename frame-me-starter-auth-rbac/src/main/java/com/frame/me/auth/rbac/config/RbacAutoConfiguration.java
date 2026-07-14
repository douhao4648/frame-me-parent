package com.frame.me.auth.rbac.config;

import com.frame.me.auth.rbac.filter.PermissionFilter;
import com.frame.me.auth.rbac.interceptor.PermissionInterceptor;
import com.frame.me.auth.rbac.permission.ConfigAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import jakarta.servlet.Filter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * RBAC 权限控制自动配置.
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "me.auth.permission", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RbacProperties.class)
public class RbacAutoConfiguration {

    /**
     * 默认配置化权限提供者.
     */
    @Bean
    @ConditionalOnMissingBean(IAuthPermissionProvider.class)
    public IAuthPermissionProvider authPermissionProvider(RbacProperties properties) {
        return new ConfigAuthPermissionProvider(properties);
    }

    /**
     * 权限拦截器，处理 Controller 方法上的权限注解.
     */
    @Bean
    public WebMvcConfigurer permissionInterceptorConfigurer(RbacProperties properties,
                                                             IAuthPermissionProvider permissionProvider,
                                                             IFilterErrorResponseWriter errorResponseWriter) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NonNull InterceptorRegistry registry) {
                registry.addInterceptor(new PermissionInterceptor(properties, permissionProvider, errorResponseWriter));
            }
        };
    }

    /**
     * 权限过滤器，按路径规则进行粗粒度权限校验.
     */
    @Bean
    public FilterRegistrationBean<Filter> permissionFilter(RbacProperties properties,
                                                            IAuthPermissionProvider permissionProvider,
                                                            IFilterErrorResponseWriter errorResponseWriter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new PermissionFilter(properties, permissionProvider, errorResponseWriter));
        registration.addUrlPatterns("/*");
        registration.setName("permissionFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 200);
        log.info("PermissionFilter registered");
        return registration;
    }
}
