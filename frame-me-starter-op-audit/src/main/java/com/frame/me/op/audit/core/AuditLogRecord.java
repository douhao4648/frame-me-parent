package com.frame.me.op.audit.core;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/**
 * 审计日志记录负载.
 *
 * <p>作为 {@link AuditLogEvent} 的 payload 在进程内或跨服务传输。</p>
 *
 * @author frame-me
 */
@Data
public class AuditLogRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 操作动作.
     */
    private String action;

    /**
     * 操作分类.
     */
    private String category;

    /**
     * 操作描述，已解析占位符.
     */
    private String description;

    /**
     * 操作人标识.
     */
    private String operatorId;

    /**
     * 业务目标标识，可为空.
     */
    private String targetId;

    /**
     * 方法入参 JSON.
     */
    private String params;

    /**
     * 返回值 JSON.
     */
    private String result;

    /**
     * 是否执行成功.
     */
    private boolean success;

    /**
     * 异常信息.
     */
    private String errorMsg;

    /**
     * 方法执行耗时，单位毫秒.
     */
    private long durationMs;

    /**
     * 事件发生时间.
     */
    private Instant timestamp;

    /**
     * 来源服务名.
     */
    private String sourceService;

    /**
     * 目标服务名，为空表示本地事件.
     */
    private String targetService;
}
