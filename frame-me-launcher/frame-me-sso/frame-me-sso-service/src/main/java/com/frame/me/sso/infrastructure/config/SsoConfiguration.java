package com.frame.me.sso.infrastructure.config;

import com.frame.me.auth.satoken.config.SaTokenAuthProperties;
import com.frame.me.sso.infrastructure.satoken.SsoDeviceInterceptor;
import com.frame.me.sso.infrastructure.satoken.SsoStpUtil;
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

    public SsoConfiguration(SsoProperties properties, SaTokenAuthProperties saTokenAuthProperties) {
        this.properties = properties;
        // 账号体系一致性 fail-fast：yml 的 me.auth.sa-token.logic-type 驱动 starter 全部认证动作，
        // SsoStpUtil.TYPE 驱动业务调用与 @SaCheck*(type=...) 注解（注解属性须编译期常量，无法读配置），
        // 两处不一致则 starter 查 login 体系、业务查 sso 体系，鉴权静默全灭——启动即暴露
        if (!SsoStpUtil.TYPE.equals(saTokenAuthProperties.getLogicType())) {
            throw new IllegalStateException(
                    "账号体系配置不一致：me.auth.sa-token.logic-type=" + saTokenAuthProperties.getLogicType()
                            + "，SsoStpUtil.TYPE=" + SsoStpUtil.TYPE
                            + "。注解 type 属性须编译期常量无法配置化，两处必须保持一致");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        SsoProperties.DeviceGate gate = properties.getDeviceGate();
        if (gate.isEnabled()) {
            // 管理端点仅接受默认设备会话（详见 DefaultDeviceInterceptor），路径可配
            registry.addInterceptor(new SsoDeviceInterceptor())
                    .addPathPatterns(gate.getPathPatterns());
        }
    }
}
