package com.frame.me.mybatis.flex.entity;

import com.mybatisflex.annotation.Column;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带乐观锁版本号的基础实体类.
 *
 * <p>继承 {@link BaseEntity}，增加 {@code version} 乐观锁字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseVersionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 乐观锁版本号.
     */
    @Column(version = true, onInsertValue = "1")
    private Integer version;

}
