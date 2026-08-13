package com.frame.me.audit.infrastructure.config;

import com.frame.me.op.audit.AuditLogEventConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * SSO 装配入口：配置属性注册 + Web 层拦截器.
 *
 * <p>遵循自动装配约定（{@code @Configuration(proxyBeanMethods=false)}），
 * 不在主启动类用 {@code @EnableConfigurationProperties}.</p>
 *
 * @author frame-me
 */
@Configuration(proxyBeanMethods = false)
@Import(AuditLogEventConfiguration.class)
public class AuditConfiguration {

}
