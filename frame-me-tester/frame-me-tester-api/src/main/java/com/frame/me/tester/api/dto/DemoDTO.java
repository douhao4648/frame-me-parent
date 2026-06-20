package com.frame.me.tester.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 演示数据请求 DTO.
 */
@Data
public class DemoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 姓名.
     */
    private String name;

    /**
     * 年龄.
     */
    private Integer age;

    /**
     * 版本号，用于乐观锁控制.
     */
    private Integer version;
}
