package com.frame.me.op.audit;

import com.frame.me.op.audit.core.AuditLogEventType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计日志事件类型显式注册配置.
 *
 * <p>由 {@link com.frame.me.op.audit.config.AuditAutoConfiguration} 引入，
 * 保证事件类型能被 {@code EventBridgeListener} 收集，不依赖消费方组件扫描路径。</p>
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
