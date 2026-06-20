package com.frame.me.base.mybatis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类，提供公共字段.
 *
 * <p>所有业务实体建议继承此类，已内置主键、创建时间、更新时间、逻辑删除等字段。
 * <p>如果需要乐观锁版本号，请继承 {@link BaseVersionEntity}。
 */
@Data
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID，雪花算法.（可重载）
     * IdType.AUTO 数据库自增
     * IdType.ASSIGN_ID 雪花ID，需配置成 k8s 环境变量
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 创建时间.
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间.
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志：0 未删除 / 1 已删除.
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
