package com.frame.me.tester.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 演示数据复杂查询返回 VO.
 */
@Data
public class DemoComplexVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID.
     */
    private Long id;

    /**
     * 姓名.
     */
    private String name;

    /**
     * 年龄.
     */
    private Integer age;

    /**
     * 年龄分组（minor/adult/senior）.
     */
    private String ageGroup;

    /**
     * 创建时间.
     */
    private LocalDateTime createTime;
}
