package com.frame.me.adapter.api.result;

import lombok.Data;

import java.util.List;

/**
 * <p>
 * 分页请求响应基础对象类
 * </p>
 *
 * @author me
 */
@Data
public class PageResult<T> {

    private long pageNum;

    private long pageSize;

    private long total;

    private long pages;

    private List<T> list;

}
