package com.frame.me.audit.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志视图对象.
 *
 * <p>字段与 {@code audit_log} 表一一对应（剔除 updateTime/deleted 内部字段）。</p>
 *
 * @author frame-me
 */
@Data
@Schema(description = "审计日志视图对象")
public class LogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志 ID")
    private Long id;

    @Schema(description = "操作动作")
    private String action;

    @Schema(description = "操作分类")
    private String category;

    @Schema(description = "操作描述，已解析占位符")
    private String description;

    @Schema(description = "操作人标识")
    private String operatorId;

    @Schema(description = "业务目标标识")
    private String targetId;

    @Schema(description = "方法入参 JSON")
    private String params;

    @Schema(description = "返回值 JSON")
    private String result;

    @Schema(description = "是否执行成功")
    private Boolean success;

    @Schema(description = "异常信息")
    private String errorMsg;

    @Schema(description = "方法执行耗时，单位毫秒")
    private Long durationMs;

    @Schema(description = "事件发生时间")
    private LocalDateTime timestamp;

    @Schema(description = "来源服务名")
    private String sourceService;

    @Schema(description = "目标服务名，为空表示本地事件")
    private String targetService;

    @Schema(description = "入库时间")
    private LocalDateTime createTime;
}
