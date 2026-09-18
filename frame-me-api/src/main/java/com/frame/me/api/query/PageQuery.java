package com.frame.me.api.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页查询参数.
 *
 * <p>不依赖任何 ORM 框架，供 API 契约层复用。</p>
 */
@Data
@Schema(description = "通用分页查询参数")
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码，默认 1.
     */
    @Min(value = 1, message = "页码必须大于 0")
    @Schema(description = "当前页码，默认 1")
    private Long current = 1L;

    /**
     * 每页条数，默认 10，最大 200（防大结果集拉垮服务/DB）.
     */
    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = 200, message = "每页条数不能超过 200")
    @Schema(description = "每页条数，默认 10，最大 200")
    private Long size = 10L;

    /**
     * 排序字段列表.
     *
     * <p>格式：{@code 字段名[ 方向]}，方向可选 {@code asc} 或 {@code desc}，省略时默认升序。
     *
     * <p><strong>安全注意：</strong>下游 ORM 实现必须在构建 ORDER BY 子句前对字段名做白名单校验，
     * 禁止直接拼接（防 SQL 注入）。</p>
     */
    @Schema(description = "排序字段列表，格式：字段名[ 方向]，方向可选 asc 或 desc，省略时默认升序")
    private List<String> orderBy;
}
