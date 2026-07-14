package com.frame.me.auth.config;

import com.frame.me.auth.audit.AuditAuthOperatorSupplier;
import com.frame.me.auth.core.HeaderAuthUserResolver;
import com.frame.me.auth.filter.AuthFilter;
import com.frame.me.auth.propagation.AuthContextTaskDecorator;
import com.frame.me.auth.propagation.AuthPropagationInterceptor;
import com.frame.me.auth.resolver.LoginUserArgumentResolver;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.base.config.AsyncAutoConfiguration;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import com.frame.me.op.audit.config.AuditAutoConfiguration;
import com.frame.me.op.audit.spi.AuditLogOperatorSupplier;
import jakarta.servlet.Filter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/**
 * 认证模块自动配置.
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "me.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuthProperties.class)
@AutoConfigureBefore({AuditAutoConfiguration.class, AsyncAutoConfiguration.class})
public class AuthAutoConfiguration {

    /**
     * 默认用户解析器：从请求头读取用户 ID 和账号.
     */
    @Bean
    @ConditionalOnMissingBean(IAuthUserResolver.class)
    public IAuthUserResolver authUserResolver() {
        return new HeaderAuthUserResolver();
    }

    /**
     * 认证过滤器，解析并设置当前用户上下文.
     */
    @Bean
    public FilterRegistrationBean<Filter> authFilter(IAuthUserResolver userResolver,
                                                      RequestMappingHandlerMapping handlerMapping,
                                                      AuthProperties properties,
                                                      IFilterErrorResponseWriter errorResponseWriter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthFilter(userResolver, handlerMapping, properties, errorResponseWriter));
        registration.addUrlPatterns("/*");
        registration.setName("authFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        log.info("AuthFilter registered");
        return registration;
    }

    /**
     * {@link com.frame.me.auth.annotation.LoginUser} 参数解析器.
     */
    @Bean
    public WebMvcConfigurer loginUserArgumentResolverConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(@NonNull List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new LoginUserArgumentResolver());
            }
        };
    }

    /**
     * 审计操作人提供者，从认证上下文获取当前用户 ID.
     */
    @Bean
    @ConditionalOnClass(AuditLogOperatorSupplier.class)
    @ConditionalOnMissingBean(AuditLogOperatorSupplier.class)
    public AuditLogOperatorSupplier auditAuthOperatorSupplier() {
        return new AuditAuthOperatorSupplier();
    }

    /**
     * 认证信息传播拦截器，用于把当前请求的认证头复制到 {@code @ImportHttpServices} 出站请求.
     */
    @Bean
    @ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
    @ConditionalOnProperty(prefix = "me.auth.propagate", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuthPropagationInterceptor authPropagationInterceptor(AuthProperties properties) {
        return new AuthPropagationInterceptor(properties);
    }

    /**
     * 为所有声明式 HTTP 客户端分组注册认证传播拦截器.
     */
    @Bean
    @ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
    @ConditionalOnProperty(prefix = "me.auth.propagate", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RestClientHttpServiceGroupConfigurer authPropagationGroupConfigurer(
            AuthPropagationInterceptor authPropagationInterceptor) {
        return groups -> groups.forEachClient((group, clientBuilder) ->
                clientBuilder.requestInterceptor(authPropagationInterceptor));
    }

    /**
     * 认证上下文异步任务装饰器.
     *
     * <p>当 {@code me.auth.propagate.async.enabled=true} 时，在默认 {@code @Async} 线程池上
     * 捕获并恢复 {@link com.frame.me.auth.core.AuthContext} 用户以及当前请求的认证头。
     * 关闭时不会注册到线程池，异步线程中无法获取认证上下文。</p>
     */
    @Bean
    @ConditionalOnClass(TaskDecorator.class)
    @ConditionalOnProperty(prefix = "me.auth.propagate.async", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuthContextTaskDecorator authContextTaskDecorator(AuthProperties properties) {
        return new AuthContextTaskDecorator(properties);
    }
}
