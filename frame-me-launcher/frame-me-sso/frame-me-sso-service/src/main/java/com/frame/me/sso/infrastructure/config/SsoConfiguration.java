package com.frame.me.sso.infrastructure.config;

import com.frame.me.sso.infrastructure.satoken.DefaultDeviceInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SSO 装配入口：配置属性注册 + Web 层拦截器.
 *
 * <p>遵循自动装配约定（{@code @Configuration(proxyBeanMethods=false)}），
 * 不在主启动类用 {@code @EnableConfigurationProperties}.</p>
 *
 * @author frame-me
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SsoProperties.class)
public class SsoConfiguration implements WebMvcConfigurer {

    private final SsoProperties properties;

    public SsoConfiguration(SsoProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        SsoProperties.DeviceGate gate = properties.getDeviceGate();
        if (gate.isEnabled()) {
            // 管理端点仅接受默认设备会话（详见 DefaultDeviceInterceptor），路径可配
            registry.addInterceptor(new DefaultDeviceInterceptor())
                    .addPathPatterns(gate.getPathPatterns());
        }
    }
}
