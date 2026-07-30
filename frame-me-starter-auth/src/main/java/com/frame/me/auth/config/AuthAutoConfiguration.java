package com.frame.me.auth.config;

import com.frame.me.auth.audit.AuditAuthOperatorSupplier;
import com.frame.me.auth.core.HeaderAuthUserResolver;
import com.frame.me.auth.filter.AuthFilter;
import com.frame.me.auth.propagation.AuthContextTaskDecorator;
import com.frame.me.auth.propagation.AuthPropagationInterceptor;
import com.frame.me.auth.resolver.LoginUserArgumentResolver;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.auth.spi.IServiceInstanceProbe;
import com.frame.me.base.config.AsyncAutoConfiguration;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import com.frame.me.op.audit.config.AuditAutoConfiguration;
import com.frame.me.op.audit.spi.IAuditLogOperatorSupplier;
import jakarta.servlet.Filter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
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
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/**
 * 认证模块自动配置.
 *
 * <p>仅 Servlet Web 应用装配：非 Web 应用（{@code spring.main.web-application-type=none}）
 * 下 {@code RequestMappingHandlerMapping} 不存在，缺此条件会导致 {@code authFilter} 装配失败、
 * 启动崩溃；非 Web 场景无请求上下文，认证过滤器/参数解析器/传播装饰器本就无意义。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "me.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuthProperties.class)
@AutoConfigureBefore({AuditAutoConfiguration.class, AsyncAutoConfiguration.class})
public class AuthAutoConfiguration {

    /**
     * 默认用户解析器：从请求头读取用户 ID 和账号.
     *
     * <p>该解析器无条件信任客户端传入的 {@code X-User-Id} 头，仅适用于不直接对外暴露的
     * 内网服务间调用场景，默认不装配，需显式配置 {@code me.auth.header-resolver.enabled=true}。
     * 对外应用应引入 auth-jwt / auth-sa-token 提供真实实现。</p>
     */
    @Bean
    @ConditionalOnMissingBean(IAuthUserResolver.class)
    @ConditionalOnProperty(prefix = "me.auth.header-resolver", name = "enabled", havingValue = "true")
    public IAuthUserResolver authUserResolver() {
        log.warn("已启用基于请求头的用户解析器（X-User-Id），该方式无条件信任客户端传入的身份头，"
                + "仅适用于前置网关已剥离外部身份头的内网服务间调用，切勿用于直接对外的服务");
        return new HeaderAuthUserResolver();
    }

    /**
     * 认证过滤器，解析并设置当前用户上下文.
     *
     * <p>缺少 {@link IAuthUserResolver} 实现时直接启动失败并给出指引，
     * 避免静默退化为不安全的默认行为。</p>
     */
    @Bean
    public FilterRegistrationBean<Filter> authFilter(ObjectProvider<IAuthUserResolver> userResolverProvider,
                                                      RequestMappingHandlerMapping handlerMapping,
                                                      AuthProperties properties,
                                                      IFilterErrorResponseWriter errorResponseWriter) {
        IAuthUserResolver userResolver = userResolverProvider.getIfAvailable(() -> {
            throw new IllegalStateException(
                    "未找到 IAuthUserResolver 实现：对外应用请引入 frame-me-starter-auth-jwt 或 "
                            + "frame-me-starter-auth-sa-token；内网服务间调用可显式配置 "
                            + "me.auth.header-resolver.enabled=true 启用基于请求头的解析器");
        });
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
    @ConditionalOnClass(IAuditLogOperatorSupplier.class)
    @ConditionalOnMissingBean(IAuditLogOperatorSupplier.class)
    public IAuditLogOperatorSupplier auditAuthOperatorSupplier() {
        return new AuditAuthOperatorSupplier();
    }

    /**
     * 认证信息传播拦截器，用于把当前请求的认证头复制到 {@code @ImportHttpServices} 出站请求.
     */
    @Bean
    @ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
    @ConditionalOnProperty(prefix = "me.auth.propagate", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuthPropagationInterceptor authPropagationInterceptor(
            AuthProperties properties,
            ObjectProvider<IServiceInstanceProbe> serviceInstanceProbeProvider) {
        if (properties.getPropagate().getAllowedHosts().isEmpty()) {
            log.info("认证信息传播未配置目标主机白名单（me.auth.propagate.allowed-hosts）："
                    + "仅向注册中心服务名及单标签内网主机传播认证头，外部域名/IP 一律不传播");
        }
        return new AuthPropagationInterceptor(properties, serviceInstanceProbeProvider.getIfAvailable());
    }

    /**
     * 注册中心服务名探针装配：classpath 存在 Spring Cloud LoadBalancer 时，
     * 以 {@code LoadBalancerClient.choose(host)} 判定目标主机是否为注册中心服务名.
     *
     * <p>独立嵌套配置 + 条件注解，保证无 Spring Cloud 的项目不会触探该类加载。
     * 业务方可注册自定义 {@link IServiceInstanceProbe} bean 覆盖默认实现。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.cloud.client.loadbalancer.LoadBalancerClient")
    static class ServiceInstanceProbeConfiguration {

        @Bean
        @ConditionalOnMissingBean(IServiceInstanceProbe.class)
        @ConditionalOnProperty(prefix = "me.auth.propagate.service-discovery", name = "enabled",
                havingValue = "true", matchIfMissing = true)
        IServiceInstanceProbe loadBalancerServiceInstanceProbe(
                ObjectProvider<org.springframework.cloud.client.loadbalancer.LoadBalancerClient> loadBalancerClient) {
            return host -> {
                try {
                    org.springframework.cloud.client.loadbalancer.LoadBalancerClient client =
                            loadBalancerClient.getIfAvailable();
                    return client != null && client.choose(host) != null;
                } catch (Exception e) {
                    log.debug("LoadBalancer 服务名探测失败: host={}, {}", host, e.getMessage());
                    return false;
                }
            };
        }
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
