package com.frame.me.op.audit.config;

import com.frame.me.op.audit.AuditLogEventConfiguration;
import com.frame.me.op.audit.aspect.AuditLogAspect;
import com.frame.me.op.audit.listener.AuditLogLogger;
import com.frame.me.op.audit.spi.AuditLogOperatorSupplier;
import com.frame.me.base.event.EventBridgeProperties;
import com.frame.me.base.event.EventBridgePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 审计日志自动配置.
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "me.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
@EnableConfigurationProperties(AuditProperties.class)
@Import(AuditLogEventConfiguration.class)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditLogOperatorSupplier auditLogOperatorSupplier() {
        return () -> "anonymous";
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogAspect auditLogAspect(EventBridgePublisher publisher,
                                          AuditLogOperatorSupplier operatorSupplier,
                                          AuditProperties properties,
                                          EventBridgeProperties eventBridgeProperties) {
        log.info("AuditLogAspect initialized, targetService={}", properties.getTargetService());
        return new AuditLogAspect(publisher, operatorSupplier, properties, eventBridgeProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogLogger auditLogLogger(AuditProperties properties) {
        return new AuditLogLogger(properties);
    }
}
