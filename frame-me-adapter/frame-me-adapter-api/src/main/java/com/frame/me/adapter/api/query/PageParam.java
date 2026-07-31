package com.frame.me.adapter.api.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Min(value = 1, message = "pageNum 必须 >= 1")
    protected Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 必须 >= 1")
    @Max(value = 500, message = "pageSize 必须 <= 500")
    protected Integer pageSize = 10;

    protected Boolean searchCount;

    protected List<OrderItem> orders;

    @Data
    public static class OrderItem {

        private String column;

        private boolean asc = true;

    }

}
