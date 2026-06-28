package com.frame.me.op.audit.core;

import com.frame.me.event.EventType;
import com.frame.me.event.MeApplicationEvent;

/**
 * 审计日志事件类型注册项.
 *
 * <p>由 {@link com.frame.me.op.audit.AuditLogEventConfiguration} 显式注册，供
 * {@code EventBridgeListener} 在跨服务接收时反序列化负载。</p>
 *
 * @author frame-me
 */
public class AuditLogEventType implements EventType<AuditLogRecord> {

    @Override
    public String type() {
        return "audit:log";
    }

    @Override
    public Class<AuditLogRecord> payloadClass() {
        return AuditLogRecord.class;
    }

    @Override
    public MeApplicationEvent toLocalEvent(AuditLogRecord payload, String source) {
        return new AuditLogEvent(source, payload, payload.getTargetService());
    }
}
