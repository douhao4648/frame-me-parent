package com.frame.me.auth.rbac.config;

import com.frame.me.auth.rbac.filter.PermissionFilter;
import com.frame.me.auth.rbac.interceptor.PermissionInterceptor;
import com.frame.me.auth.rbac.permission.ConfigAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.propagation.AuthPermissionTaskDecorator;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import jakarta.servlet.Filter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * RBAC 权限控制自动配置.
 *
 * <p>仅 Servlet Web 应用装配：权限校验是 Web 请求关注点，
 * 非 Web 应用下 Filter/拦截器无挂载点，整体退避。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "me.auth.permission", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RbacProperties.class)
public class RbacAutoConfiguration {

    /**
     * 默认配置化权限提供者，即权限数据源插槽（bean 名 {@code authPermissionSource}）.
     *
     * <p>业务声明任意 {@link IAuthPermissionProvider} bean 即可使本默认实现退避；
     * 启用 Redis 后端时，{@code RbacRedisAutoConfiguration} 的 {@code @Primary} 包装器
     * 按名引用本插槽作为委托数据源。</p>
     */
    @Bean(name = "authPermissionSource")
    @ConditionalOnMissingBean(IAuthPermissionProvider.class)
    public IAuthPermissionProvider authPermissionSource(RbacProperties properties) {
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

    /**
     * 权限上下文异步任务装饰器.
     *
     * <p>当 {@code me.auth.permission.propagate.async.enabled=true} 时，在默认 {@code @Async}
     * 线程池上捕获并恢复 {@link com.frame.me.auth.rbac.permission.AuthPermissionHolder}，
     * 使异步方法内的 {@code role()/perm()} 判断生效。</p>
     */
    @Bean
    @ConditionalOnClass(TaskDecorator.class)
    @ConditionalOnProperty(prefix = "me.auth.permission.propagate.async", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuthPermissionTaskDecorator authPermissionTaskDecorator() {
        return new AuthPermissionTaskDecorator();
    }
}
