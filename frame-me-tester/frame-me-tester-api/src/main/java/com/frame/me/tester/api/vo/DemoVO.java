package com.frame.me.tester.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 演示数据返回 VO.
 */
@Data
public class DemoVO implements Serializable {

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
     * 版本号.
     */
    private Integer version;

    /**
     * 创建时间.
     */
    private LocalDateTime createTime;

    /**
     * 更新时间.
     */
    private LocalDateTime updateTime;
}
