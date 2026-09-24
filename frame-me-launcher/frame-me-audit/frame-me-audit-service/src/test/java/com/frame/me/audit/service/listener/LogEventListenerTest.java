package com.frame.me.audit.service.listener;

import com.frame.me.audit.entity.LogEntity;
import com.frame.me.audit.mapper.LogMapper;
import com.frame.me.op.audit.core.AuditLogEvent;
import com.frame.me.op.audit.core.AuditLogRecord;
import com.frame.me.redis.util.RedissonLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LogEventListener} 单元测试：Redis 锁去重语义、eventId 入库、
 * event_id 唯一约束冲突视为去重成功（兜底锁窗口外重放与 Redis 降级重复）.
 *
 * @author frame-me
 */
@ExtendWith(MockitoExtension.class)
class LogEventListenerTest {

    @Mock
    private LogMapper auditLogMapper;
    @Mock
    private RedissonLock redissonLock;
    @InjectMocks
    private LogEventListener listener;

    /**
     * 拿到锁：eventId 写入实体后入库.
     */
    @Test
    void onAuditLog_persistsEventIdWhenLockAcquired() {
        when(redissonLock.tryLock(anyString(), anyLong(), anyLong())).thenReturn(true);
        AuditLogEvent event = event("evt-1");

        listener.onAuditLog(event);

        ArgumentCaptor<LogEntity> captor = ArgumentCaptor.forClass(LogEntity.class);
        verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("evt-1");
        assertThat(captor.getValue().getAction()).isEqualTo("login");
    }

    /**
     * 锁被其他实例持有：跳过入库.
     */
    @Test
    void onAuditLog_skipsWhenLockHeldByOtherInstance() {
        when(redissonLock.tryLock(anyString(), anyLong(), anyLong())).thenReturn(false);

        listener.onAuditLog(event("evt-2"));

        verify(auditLogMapper, never()).insert(any(LogEntity.class));
    }

    /**
     * event_id 唯一冲突：锁窗口外重放 / Redis 降级重复的最后一道防线，不得上抛.
     */
    @Test
    void onAuditLog_duplicateKeyTreatedAsDedupSuccess() {
        when(redissonLock.tryLock(anyString(), anyLong(), anyLong())).thenReturn(true);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("Duplicate entry 'evt-3' for key 'event_id'"))
                .when(auditLogMapper).insert(any(LogEntity.class));

        assertThatCode(() -> listener.onAuditLog(event("evt-3"))).doesNotThrowAnyException();
    }

    /**
     * 兼容旧链路：无 eventId 时不碰锁、直接入库，eventId 列为 null.
     */
    @Test
    void onAuditLog_nullEventIdPersistsDirectlyWithoutLock() {
        AuditLogEvent event = event(null);

        listener.onAuditLog(event);

        verify(redissonLock, never()).tryLock(anyString(), anyLong(), anyLong());
        ArgumentCaptor<LogEntity> captor = ArgumentCaptor.forClass(LogEntity.class);
        verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getEventId()).isNull();
    }

    private AuditLogEvent event(String eventId) {
        AuditLogRecord record = new AuditLogRecord();
        record.setAction("login");
        AuditLogEvent event = new AuditLogEvent("test", record, null, "instance-1");
        event.setEventId(eventId);
        return event;
    }
}
