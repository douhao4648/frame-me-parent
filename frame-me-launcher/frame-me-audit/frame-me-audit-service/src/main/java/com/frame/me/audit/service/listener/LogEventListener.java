package com.frame.me.audit.service.listener;

import com.frame.me.audit.entity.LogEntity;
import com.frame.me.audit.mapper.LogMapper;
import com.frame.me.op.audit.core.AuditLogEvent;
import com.frame.me.op.audit.core.AuditLogRecord;
import com.frame.me.redis.util.RedissonLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 审计日志持久化处理器.
 *
 * <p>{@code @Import(AuditLogEventConfiguration.class)}（启动类声明）注册
 * {@code AuditLogEventType} 后，{@code EventBridgeListener} 订阅 {@code audit:log}
 * 通道，把跨服务广播的审计事件还原为本地 {@link AuditLogEvent}。本处理器收到后
 * 取 {@link AuditLogRecord} 写入 {@code audit_log} 表。</p>
 *
 * <p><b>多实例去重</b>：audit 多实例部署时，同一广播事件会被每个实例收到。
 * 以 {@link AuditLogEvent#getEventId()} 为 key 加 Redis 分布式锁
 * （{@code audit:log:dedup:<eventId>}），全局只入库一次。Redis 故障降级放行
 * （审计是旁路，宁可重复不可丢失）；锁 waitMs=0 不阻塞，leaseMs=30s。</p>
 *
 * <p><b>锁不主动释放</b>：insert 完后<b>不</b> unlock，靠 leaseMs=30s 自动过期。
 * 这样同一 Pub/Sub 广播被多个在线审计实例收到时，在 TTL 窗口内只有一个实例可以入库。
 * 若 unlock，锁只覆盖单次 insert，其他实例随后仍可能重复入库。
 * 代价：持锁实例 insert 失败时锁仍占用 TTL，该 eventId 的日志在窗口内丢失
 * （遵"审计是旁路"原则，DB 故障下丢日志可接受）。</p>
 *
 * <p>持久化失败不阻断事件链路（审计是旁路，不能因持久化失败影响主业务）。</p>
 *
 * @author frame-me
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogEventListener {

    private static final String LOCK_KEY_PREFIX = "audit:log:dedup:";

    private final LogMapper auditLogMapper;
    private final RedissonLock redissonLock;

    @EventListener
    public void onAuditLog(AuditLogEvent event) {
        String eventId = event.getEventId();
        if (eventId == null || eventId.isBlank()) {
            // 兼容旧链路无 eventId：直接入库（去重降级）
            persist(event.getRecord());
            return;
        }
        String lockKey = LOCK_KEY_PREFIX + eventId;
        boolean locked;
        try {
            // waitMs=0 不阻塞 listener 线程；leaseMs=30s 兜底防持锁实例宕机后锁不释放
            locked = redissonLock.tryLock(lockKey, 0, 30_000);
        } catch (Exception e) {
            // Redis 故障：降级放行（旁路原则，宁可重复不可丢失）
            log.warn("审计去重锁失败，降级直接入库: eventId={}", eventId, e);
            locked = true;
        }
        if (!locked) {
            log.debug("审计日志已由其他实例入库，跳过: eventId={}", eventId);
            return;
        }
        // 不主动 unlock：靠 leaseMs=30s 自动过期，覆盖多实例广播副本的到达窗口（见类 Javadoc）
        persist(event.getRecord());
    }

    /**
     * 持久化审计记录，失败不阻断事件链路.
     */
    private void persist(AuditLogRecord record) {
        try {
            LogEntity entity = toEntity(record);
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("审计日志持久化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * AuditLogRecord → LogEntity.
     *
     * <p>{@code Instant timestamp} → {@code LocalDateTime}（系统默认时区）。</p>
     */
    private LogEntity toEntity(AuditLogRecord r) {
        LogEntity e = new LogEntity();
        e.setAction(r.getAction());
        e.setCategory(r.getCategory());
        e.setDescription(r.getDescription());
        e.setOperatorId(r.getOperatorId());
        e.setTargetId(r.getTargetId());
        e.setParams(r.getParams());
        e.setResult(r.getResult());
        e.setSuccess(r.isSuccess());
        e.setErrorMsg(r.getErrorMsg());
        e.setDurationMs(r.getDurationMs());
        e.setTimestamp(r.getTimestamp() == null ? null
                : LocalDateTime.ofInstant(r.getTimestamp(), ZoneId.systemDefault()));
        e.setSourceService(r.getSourceService());
        e.setTargetService(r.getTargetService());
        return e;
    }
}
