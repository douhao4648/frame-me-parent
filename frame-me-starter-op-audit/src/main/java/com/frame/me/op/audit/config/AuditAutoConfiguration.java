package com.frame.me.op.audit.config;

import com.frame.me.base.event.EventBridgeProperties;
import com.frame.me.base.event.EventBridgePublisher;
import com.frame.me.op.audit.aspect.AuditLogAspect;
import com.frame.me.op.audit.listener.AuditLogLogger;
import com.frame.me.op.audit.spi.IAuditLogOperatorSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计日志自动配置.
 *
 * <p>{@link EventBridgeProperties} 在此兜底注册：桥接开启时由 base 侧
 * {@code EventBridgeAutoConfiguration} 注册（重复注册会被跳过），桥接关闭
 * （{@code me.event-bridge.enabled=false}）时由本配置提供，保证审计模块
 * 不依赖桥接开关即可启动，审计事件降级为仅本地发布。</p>
 *
 * <p>刻意不自动注册 {@code AuditLogEventType}：事件类型注册会触发
 * {@code EventBridgeListener} 订阅对应 Redis 通道，而该类型仅<b>接收侧</b>
 * （审计中心）需要——发送侧经 {@code EventBridgePublisher} 直接发，不查注册表。
 * 自动注册会让所有引入本 starter 的服务白订阅一个用不到的通道。
 * 审计中心一行 {@code @Import(AuditLogEventConfiguration.class)} 显式启用即可。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "me.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
@EnableConfigurationProperties({AuditProperties.class, EventBridgeProperties.class})
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IAuditLogOperatorSupplier auditLogOperatorSupplier() {
        return () -> "anonymous";
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogAspect auditLogAspect(ApplicationEventPublisher localPublisher,
                                         ObjectProvider<EventBridgePublisher> bridgePublisherProvider,
                                         IAuditLogOperatorSupplier operatorSupplier,
                                         AuditProperties properties,
                                         EventBridgeProperties eventBridgeProperties) {
        log.info("AuditLogAspect initialized, targetService={}", properties.getTargetService());
        return new AuditLogAspect(localPublisher, bridgePublisherProvider, operatorSupplier,
                properties, eventBridgeProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogLogger auditLogLogger(AuditProperties properties,
                                         EventBridgeProperties eventBridgeProperties) {
        return new AuditLogLogger(properties, eventBridgeProperties);
    }
}
