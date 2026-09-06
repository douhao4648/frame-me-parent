package com.frame.me.op.audit.core;

import com.frame.me.event.IEventType;
import com.frame.me.event.AbstractMeApplicationEvent;

/**
 * 审计日志事件类型注册项.
 *
 * <p>由 {@link com.frame.me.op.audit.AuditLogEventConfiguration} 显式注册，供
 * {@code EventBridgeListener} 在跨服务接收时反序列化负载。</p>
 *
 * @author frame-me
 */
public class AuditLogEventType implements IEventType<AuditLogRecord> {

    @Override
    public String type() {
        return "audit:log";
    }

    @Override
    public Class<AuditLogRecord> payloadClass() {
        return AuditLogRecord.class;
    }

    @Override
    public AbstractMeApplicationEvent toLocalEvent(AuditLogRecord payload, String source, String sourceInstanceId) {
        return new AuditLogEvent(source, payload, payload.getTargetService(), sourceInstanceId);
    }
}
