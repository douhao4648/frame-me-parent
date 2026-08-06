package com.frame.me.sso.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SSO 配置属性注册.
 *
 * <p>单独配置类注册 {@link SsoProperties}，遵循自动装配约定
 * （{@code @Configuration(proxyBeanMethods=false)} + 独立 Config 类），
 * 不在主启动类用 {@code @EnableConfigurationProperties}.</p>
 *
 * @author frame-me
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SsoProperties.class)
public class SsoConfiguration {
}
