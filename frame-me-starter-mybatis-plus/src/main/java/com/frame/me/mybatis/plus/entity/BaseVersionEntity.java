package com.frame.me.mybatis.plus.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带乐观锁版本号的基础实体类.
 *
 * <p>继承 {@link BaseEntity}，额外提供 {@code version} 字段。
 * <p>只有需要乐观锁控制的业务实体才继承此类；普通业务实体继承 {@link BaseEntity} 即可。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseVersionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 乐观锁版本号.
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
