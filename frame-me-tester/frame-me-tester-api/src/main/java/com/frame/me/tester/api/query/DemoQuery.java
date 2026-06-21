package com.frame.me.tester.api.query;

import com.frame.me.api.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 演示数据分页查询参数.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "演示数据分页查询参数")
public class DemoQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 姓名，支持模糊查询.
     */
    @Schema(description = "姓名，支持模糊查询")
    private String name;

    /**
     * 年龄，精确查询.
     */
    @Schema(description = "年龄，精确查询")
    private Integer age;
}
