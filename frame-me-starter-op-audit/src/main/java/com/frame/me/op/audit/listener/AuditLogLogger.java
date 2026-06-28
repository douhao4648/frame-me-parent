package com.frame.me.op.audit.listener;

import com.alibaba.fastjson2.JSON;
import com.frame.me.op.audit.config.AuditProperties;
import com.frame.me.op.audit.core.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

/**
 * 审计日志本地监听器.
 *
 * <p>默认将 {@link AuditLogEvent} 输出到 SLF4J，可通过 {@code me.audit.log-enabled=false}
 * 关闭。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class AuditLogLogger {

    private final AuditProperties properties;

    /**
     * 监听审计事件并打印结构化日志.
     *
     * @param event 审计事件
     */
    @EventListener
    public void onAuditLog(AuditLogEvent event) {
        if (!properties.isLogEnabled()) {
            return;
        }
        log.info("[AUDIT] {}", JSON.toJSONString(event.getRecord()));
    }
}
