package com.frame.me.op.audit.core;

import com.frame.me.event.MeApplicationEvent;
import lombok.Getter;

/**
 * 审计日志事件.
 *
 * <p>继承 {@link MeApplicationEvent}，可通过 {@code me.audit.target-service}
 * 配置决定是否桥接到专门的审计服务。</p>
 *
 * @author frame-me
 */
public class AuditLogEvent extends MeApplicationEvent {

    private static final long serialVersionUID = 1L;

    @Getter
    private final AuditLogRecord record;

    private final String targetService;

    /**
     * 构造审计日志事件.
     *
     * @param source       事件源，通常为来源服务名
     * @param record       审计记录
     * @param targetService 目标服务名，可为 null
     */
    public AuditLogEvent(Object source, AuditLogRecord record, String targetService) {
        super(source);
        this.record = record;
        this.targetService = targetService;
    }

    @Override
    public String getEventType() {
        return "audit:log";
    }

    @Override
    public Object getPayload() {
        return record;
    }

    @Override
    public String getTargetService() {
        return targetService;
    }
}
