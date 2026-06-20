package com.frame.me.tester.api.query;

import com.frame.me.api.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 演示数据分页查询参数.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DemoQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 姓名，支持模糊查询.
     */
    private String name;

    /**
     * 年龄，精确查询.
     */
    private Integer age;
}
