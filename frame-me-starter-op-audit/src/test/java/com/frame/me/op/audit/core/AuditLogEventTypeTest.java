package com.frame.me.op.audit.core;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditLogEventType} 单元测试.
 *
 * @author frame-me
 */
class AuditLogEventTypeTest {

    private final AuditLogEventType eventType = new AuditLogEventType();

    @Test
    void shouldRoundTripPayload() {
        AuditLogRecord record = new AuditLogRecord();
        record.setAction("创建用户");
        record.setCategory("用户管理");
        record.setOperatorId("operator-1");
        record.setSuccess(true);
        record.setDurationMs(12L);
        record.setTimestamp(Instant.parse("2026-06-28T10:00:00Z"));
        record.setSourceService("order-service");
        record.setTargetService("audit-service");
        record.setParams("{\"userId\":1}");
        record.setResult("{\"id\":1}");

        String json = JSON.toJSONString(record);
        AuditLogRecord parsed = JSON.parseObject(json, AuditLogRecord.class);

        assertThat(parsed.getAction()).isEqualTo("创建用户");
        assertThat(parsed.getSourceService()).isEqualTo("order-service");
        assertThat(parsed.getTargetService()).isEqualTo("audit-service");
        assertThat(parsed.isSuccess()).isTrue();
    }

    @Test
    void shouldConvertToLocalEvent() {
        AuditLogRecord record = new AuditLogRecord();
        record.setAction("删除用户");
        record.setTargetService("audit-service");

        AuditLogEvent event = (AuditLogEvent) eventType.toLocalEvent(record, "order-service");

        assertThat(event.getEventType()).isEqualTo("audit:log");
        assertThat(event.getTargetService()).isEqualTo("audit-service");
        assertThat(event.getRecord().getAction()).isEqualTo("删除用户");
    }
}
