package com.frame.me.auth.rbac.permission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 数据权限值对象.
 *
 * @author frame-me
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 资源标识.
     */
    private String resource;

    /**
     * 操作标识.
     */
    private String action;

    /**
     * 数据范围，如 {@code ALL}、{@code DEPT}、{@code SELF}.
     */
    private String dataScope;

    /**
     * 可访问的数据 ID 集合.
     */
    private Set<Long> dataIds = new HashSet<>();

    /**
     * 判断当前数据权限是否包含指定数据 ID.
     *
     * @param dataId 数据 ID
     * @return 是否包含
     */
    public boolean contains(Long dataId) {
        if (dataIds == null || dataIds.isEmpty()) {
            return false;
        }
        return dataIds.contains(dataId);
    }
}
