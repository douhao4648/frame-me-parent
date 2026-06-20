package com.frame.me.api.query;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页查询参数.
 *
 * <p>不依赖任何 ORM 框架，供 API 契约层复用。</p>
 */
@Data
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码，默认 1.
     */
    private Long current = 1L;

    /**
     * 每页条数，默认 10.
     */
    private Long size = 10L;

    /**
     * 排序字段列表.
     *
     * <p>格式：{@code 字段名[:方向]}，方向可选 {@code asc} 或 {@code desc}，省略时默认升序。</p>
     */
    private List<String> orderBy;
}
