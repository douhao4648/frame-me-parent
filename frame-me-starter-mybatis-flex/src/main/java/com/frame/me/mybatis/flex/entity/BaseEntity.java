package com.frame.me.mybatis.flex.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体类.
 *
 * <p>提供雪花算法主键、创建时间、更新时间、逻辑删除标志等公共字段。
 * 普通业务实体建议继承此类。</p>
 */
@Data
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID，雪花算法.
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 创建时间，插入时自动填充.
     */
    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    /**
     * 更新时间，插入和更新时自动填充.
     */
    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志：0 未删除 / 1 已删除.
     */
    @Column(isLogicDelete = true, onInsertValue = "0")
    private Integer deleted;
}
