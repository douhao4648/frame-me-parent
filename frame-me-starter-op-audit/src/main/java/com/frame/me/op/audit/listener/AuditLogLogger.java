package com.frame.me.op.audit.listener;

import com.alibaba.fastjson2.JSON;
import com.frame.me.base.event.EventBridgeProperties;
import com.frame.me.op.audit.config.AuditProperties;
import com.frame.me.op.audit.core.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.util.Objects;

/**
 * 审计日志本地监听器.
 *
 * <p>默认将本服务产生的 {@link AuditLogEvent} 输出到 SLF4J，可通过
 * {@code me.audit.log-enabled=false} 关闭。</p>
 *
 * <p>审计事件默认向跨服务通道广播，所有订阅方都会本地重发布该事件；
 * 本监听器只打印本服务自己产生的事件（事件源等于当前服务名），
 * 避免每个服务实例都重复打印全量审计日志。跨服务聚合应由审计中心
 * 注册专用消费者负责。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class AuditLogLogger {

    private final AuditProperties properties;
    private final EventBridgeProperties eventBridgeProperties;

    /**
     * 监听审计事件并打印结构化日志（仅限本服务产生的事件）.
     *
     * @param event 审计事件
     */
    @EventListener
    public void onAuditLog(AuditLogEvent event) {
        if (!properties.isLogEnabled()) {
            return;
        }
        if (!Objects.equals(eventBridgeProperties.getServiceName(), event.getSource())) {
            log.debug("跳过其他服务广播的审计事件: source={}", event.getSource());
            return;
        }
        log.info("[AUDIT] {}", JSON.toJSONString(event.getRecord()));
    }
}
