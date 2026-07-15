package com.frame.me.auth.rbac.permission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 资源/操作权限值对象.
 *
 * @author frame-me
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 资源标识，如 {@code user}、{@code order}.
     */
    private String resource;

    /**
     * 操作标识，如 {@code read}、{@code create}、{@code update}、{@code delete}、{@code *}.
     */
    private String action;

    /**
     * 判断当前权限是否匹配指定资源与操作.
     *
     * <p>通配符 {@code *} 只允许出现在<b>授权侧</b>（即当前权限的 {@code resource}/{@code action}），
     * 为 {@code *} 时匹配任意对应项；被请求侧传 {@code *} 不会被当作通配。
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
     * 判断当前权限的资源是否覆盖指定资源（不校验操作）.
     *
     * <p>授权侧 {@code resource} 为 {@code *} 时匹配任意资源；比较忽略大小写。</p>
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
}
