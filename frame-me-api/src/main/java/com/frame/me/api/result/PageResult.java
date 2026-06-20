package com.frame.me.api.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果.
 *
 * @param <T> 数据类型
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码.
     */
    private Long current;

    /**
     * 每页条数.
     */
    private Long size;

    /**
     * 总记录数.
     */
    private Long total;

    /**
     * 总页数.
     */
    private Long pages;

    /**
     * 数据列表.
     */
    private List<T> records;
}
