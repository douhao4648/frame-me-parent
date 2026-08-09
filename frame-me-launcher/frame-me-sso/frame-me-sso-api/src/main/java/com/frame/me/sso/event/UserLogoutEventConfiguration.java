package com.frame.me.sso.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户登出事件类型显式注册配置.
 *
 * <p>消费方通过 {@code @Import(UserLogoutEventConfiguration.class)} 引入，
 * 即可将 {@link UserLogoutEventType} 注册到 Spring 上下文，供 {@code EventBridgeListener} 收集。
 * SSO 服务自身同样引入，保证多实例部署时踢人广播在其他实例本地重发布。</p>
 *
 * @author frame-me
 */
@Configuration(proxyBeanMethods = false)
public class UserLogoutEventConfiguration {

    @Bean
    public UserLogoutEventType userLogoutEventType() {
        return new UserLogoutEventType();
    }
}
