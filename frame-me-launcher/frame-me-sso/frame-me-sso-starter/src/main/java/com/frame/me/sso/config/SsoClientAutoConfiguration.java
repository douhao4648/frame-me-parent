package com.frame.me.sso.config;

import com.frame.me.sso.api.IAppApi;
import com.frame.me.sso.api.IAuthApi;
import com.frame.me.sso.api.IUserApi;
import com.frame.me.sso.event.UserLogoutEventConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * SSO 客户端自动配置.
 *
 * <p>注册 SSO 的 {@link IAuthApi} / {@link IUserApi} / {@link IAppApi} HTTP Interface 客户端代理
 * （group = {@code sso}），下游直接注入 {@code IAuthApi} / {@code IUserApi} 即可调用 SSO，
 * 复用 base 的池化 RestClient。group 的 baseUrl 由下游配置
 * {@code spring.http.serviceclient.sso.base-url}（Spring Boot 原生 HTTP Interface group 配置，
 * 见 base 的 {@code HttpServiceClientAutoConfiguration}）。</p>
 *
 * <p>同时 {@code @Import(UserLogoutEventConfiguration.class)} 注册 {@code UserLogoutEventType}，
 * 使 {@code EventBridgeListener} 订阅 {@code sso:user-logout} 通道——下游写
 * {@code @EventListener(UserLogoutEvent)} 即可消费 SSO 踢人事件，清自己的本地 session。</p>
 *
 * <p><b>与认证底座解耦</b>：本 starter 只提供"换 token + 取用户信息 + 事件订阅"，
 * 建本地 session（sa-token / JWT）由下游自行选择。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
@ConditionalOnProperty(prefix = "me.sso.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SsoClientProperties.class)
@Import(UserLogoutEventConfiguration.class)
public class SsoClientAutoConfiguration {

    /**
     * 声明 SSO 客户端分组，注册 {@link IAuthApi} / {@link IUserApi} / {@link IAppApi} 代理.
     *
     * <p>独立内部类承载 {@code @ImportHttpServices}，与自动配置主类分离，
     * 遵循 base 的 {@code HttpServiceClientAutoConfiguration} 模式。
     * group 名 {@code sso} 对应下游配置 {@code spring.http.serviceclient.sso.base-url}。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ImportHttpServices(group = "sso", types = {IAuthApi.class, IUserApi.class, IAppApi.class})
    static class SsoHttpServicesRegistrar {
    }
}
