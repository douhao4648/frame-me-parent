package com.frame.me.tester.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户创建事件类型显式注册配置.
 *
 * <p>消费方通过 {@code @Import(UserCreatedEventConfiguration.class)} 引入，
 * 即可将 {@link UserCreatedEventType} 注册到 Spring 上下文，供 {@code EventBridgeListener} 收集。</p>
 *
 * @author frame-me
 */
@Configuration(proxyBeanMethods = false)
public class UserCreatedEventConfiguration {

    @Bean
    public UserCreatedEventType userCreatedEventType() {
        return new UserCreatedEventType();
    }
}
