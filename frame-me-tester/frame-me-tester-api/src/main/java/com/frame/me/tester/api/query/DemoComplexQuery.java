package com.frame.me.tester.api.query;

import com.frame.me.api.query.PageQuery;
import com.frame.me.validation.annotation.TimeRange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 演示数据复杂查询参数.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TimeRange
@Schema(description = "演示数据复杂查询参数")
public class DemoComplexQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 最小年龄.
     */
    @Min(value = 0, message = "最小年龄必须大于等于 0")
    @Max(value = 150, message = "最小年龄不能超过 150")
    @Schema(description = "最小年龄")
    private Integer minAge;

    /**
     * 最大年龄.
     */
    @Min(value = 0, message = "最大年龄必须大于等于 0")
    @Max(value = 150, message = "最大年龄不能超过 150")
    @Schema(description = "最大年龄")
    private Integer maxAge;

    /**
     * 创建时间起始.
     */
    @Schema(description = "创建时间起始")
    private LocalDateTime startTime;

    /**
     * 创建时间截止.
     */
    @Schema(description = "创建时间截止")
    private LocalDateTime endTime;
}
