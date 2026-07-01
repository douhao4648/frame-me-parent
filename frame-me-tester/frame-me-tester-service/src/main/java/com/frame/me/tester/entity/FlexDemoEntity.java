package com.frame.me.tester.entity;

import com.frame.me.mybatis.flex.entity.BaseVersionEntity;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Flex 演示实体，对应表 {@code flex_demo}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("flex_demo")
public class FlexDemoEntity extends BaseVersionEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 姓名.
     */
    private String name;

    /**
     * 年龄.
     */
    private Integer age;
}
