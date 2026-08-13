package com.frame.me.audit.api.query;

import com.frame.me.api.query.PageQuery;
import com.frame.me.validation.annotation.TimeRange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审计日志查询参数（list / page 共用）.
 *
 * <p>action、operatorId、description 模糊匹配；category、sourceService、success 精确匹配；
 * startTime/endTime 按事件发生时间（timestamp 列）圈定区间。</p>
 *
 * @author frame-me
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TimeRange
@Schema(description = "审计日志查询参数")
public class LogQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 操作动作，模糊查询.
     */
    @Size(max = 64, message = "操作动作长度不能超过 64")
    @Schema(description = "操作动作，模糊查询")
    private String action;

    /**
     * 操作分类，精确查询.
     */
    @Size(max = 64, message = "操作分类长度不能超过 64")
    @Schema(description = "操作分类，精确查询")
    private String category;

    /**
     * 操作人标识，模糊查询.
     */
    @Size(max = 64, message = "操作人标识长度不能超过 64")
    @Schema(description = "操作人标识，模糊查询")
    private String operatorId;

    /**
     * 操作描述，模糊查询.
     */
    @Size(max = 128, message = "操作描述长度不能超过 128")
    @Schema(description = "操作描述，模糊查询")
    private String description;

    /**
     * 来源服务名，精确查询.
     */
    @Size(max = 64, message = "来源服务名长度不能超过 64")
    @Schema(description = "来源服务名，精确查询")
    private String sourceService;

    /**
     * 是否执行成功，精确查询.
     */
    @Schema(description = "是否执行成功，精确查询")
    private Boolean success;

    /**
     * 事件发生时间起始.
     */
    @Schema(description = "事件发生时间起始")
    private LocalDateTime startTime;

    /**
     * 事件发生时间截止.
     */
    @Schema(description = "事件发生时间截止")
    private LocalDateTime endTime;
}
