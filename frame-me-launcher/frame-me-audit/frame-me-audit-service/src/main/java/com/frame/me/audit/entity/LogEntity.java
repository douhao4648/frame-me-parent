package com.frame.me.audit.entity;

import com.frame.me.mybatis.flex.entity.BaseEntity;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审计日志实体.
 *
 * <p>对应 {@code audit_log} 表，字段映射 {@link com.frame.me.op.audit.core.AuditLogRecord}。
 * 继承 {@link BaseEntity}（雪花 id + createTime/updateTime/deleted）。</p>
 *
 * @author frame-me
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("audit_log")
public class LogEntity extends BaseEntity {

    /**
     * 事件幂等标识（{@code AbstractMeApplicationEvent.eventId}）.
     *
     * <p>数据库唯一约束兜底去重：Redis 锁只覆盖 30s 广播副本窗口，窗口外重放
     * 与 Redis 故障降级重复由该约束拦截。旧链路无 eventId 时为 {@code null}
     * （唯一索引允许多个 NULL，互不冲突）。</p>
     */
    @Column
    private String eventId;

    /**
     * 操作动作.
     */
    @Column
    private String action;

    /**
     * 操作分类.
     */
    @Column
    private String category;

    /**
     * 操作描述，已解析占位符.
     */
    @Column
    private String description;

    /**
     * 操作人标识.
     */
    @Column
    private String operatorId;

    /**
     * 业务目标标识，可为空.
     */
    @Column
    private String targetId;

    /**
     * 方法入参 JSON.
     */
    @Column
    private String params;

    /**
     * 返回值 JSON.
     */
    @Column
    private String result;

    /**
     * 是否执行成功.
     */
    @Column
    private Boolean success;

    /**
     * 异常信息.
     */
    @Column
    private String errorMsg;

    /**
     * 方法执行耗时，单位毫秒.
     */
    @Column
    private Long durationMs;

    /**
     * 事件发生时间（来自 AuditLogRecord.timestamp）.
     */
    @Column
    private LocalDateTime timestamp;

    /**
     * 来源服务名.
     */
    @Column
    private String sourceService;

    /**
     * 目标服务名，为空表示本地事件.
     */
    @Column
    private String targetService;
}
