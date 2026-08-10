package com.frame.me.op.audit;

import com.frame.me.op.audit.core.AuditLogEventType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计日志事件类型显式注册配置.
 *
 * <p>仅<b>接收侧</b>（审计中心）需要：注册 {@link com.frame.me.op.audit.core.AuditLogEventType}
 * 后 {@code EventBridgeListener} 才会订阅 {@code audit:op-log} 通道并把消息还原为本地事件。
 * 发送侧经 {@code EventBridgePublisher} 直接发，不查注册表，无需本配置；
 * 本地打印场景（{@code AuditLogLogger} 走进程内 {@code @EventListener}）同样不需要。</p>
 *
 * <p>刻意不随 {@code AuditAutoConfiguration} 自动装配：自动注册会让所有引入
 * op-audit 的服务白订阅一个用不到的 Redis 通道。审计中心在启动类或任意配置类上
 * {@code @Import(AuditLogEventConfiguration.class)} 显式启用。</p>
 *
 * @author frame-me
 */
@Configuration(proxyBeanMethods = false)
public class AuditLogEventConfiguration {

    @Bean
    public AuditLogEventType auditLogEventType() {
        return new AuditLogEventType();
    }
}
