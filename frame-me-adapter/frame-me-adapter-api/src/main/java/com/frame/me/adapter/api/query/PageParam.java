package com.frame.me.adapter.api.query;

import lombok.Data;

import java.util.List;

/**
 * <p>
 * 分页请求参数基础对象类
 * </p>
 *
 * @author me
 */
@Data
public class PageParam {

    protected Integer pageNum = 1;

    protected Integer pageSize = 10;

    protected Boolean searchCount;

    protected List<OrderItem> orders;

    @Data
    public static class OrderItem {

        private String column;

        private boolean asc = true;

    }

}
