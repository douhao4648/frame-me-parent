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
     * <p>支持 {@code *} 通配：{@code resource} 或 {@code action} 为 {@code *} 时匹配任意对应项。
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

    private boolean matchesValue(String pattern, String value) {
        return "*".equals(pattern) || "*".equals(value) || pattern.equalsIgnoreCase(value);
    }
}
