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
     * 判断当前数据权限是否匹配指定资源与操作.
     *
     * <p>通配符 {@code *} 只允许出现在<b>授权侧</b>（即当前数据权限的 {@code resource}/{@code action}），
     * 比较时忽略大小写。</p>
     *
     * @param targetResource 目标资源
     * @param targetAction   目标操作
     * @return 是否匹配
     */
    public boolean matches(String targetResource, String targetAction) {
        if (targetResource == null || targetAction == null) {
            return false;
        }
        return matchesValue(resource, targetResource) && matchesValue(action, targetAction);
    }

    /**
     * 判断当前数据权限的资源是否覆盖指定资源（不校验操作）.
     *
     * @param targetResource 目标资源
     * @return 是否覆盖
     */
    public boolean matchesResource(String targetResource) {
        if (targetResource == null) {
            return false;
        }
        return matchesValue(resource, targetResource);
    }

    private boolean matchesValue(String pattern, String value) {
        return "*".equals(pattern) || pattern.equalsIgnoreCase(value);
    }

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
