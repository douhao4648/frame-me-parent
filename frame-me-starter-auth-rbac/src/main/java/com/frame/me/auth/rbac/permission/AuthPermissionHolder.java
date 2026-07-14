package com.frame.me.auth.rbac.permission;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 认证权限线程本地持有器.
 *
 * <p>缓存当前请求解析出的角色和权限，避免同一请求多次调用 {@link IAuthPermissionProvider}。</p>
 *
 * @author frame-me
 */
public class AuthPermissionHolder {

    private static final ThreadLocal<Set<String>> ROLES = new ThreadLocal<>();
    private static final ThreadLocal<Set<Permission>> PERMISSIONS = new ThreadLocal<>();

    private AuthPermissionHolder() {
    }

    /**
     * 设置当前线程的角色集合.
     *
     * @param roles 角色集合
     */
    public static void setRoles(Collection<String> roles) {
        ROLES.set(roles == null ? Collections.emptySet() : new HashSet<>(roles));
    }

    /**
     * 获取当前线程的角色集合.
     *
     * @return 角色集合
     */
    public static Set<String> getRoles() {
        Set<String> roles = ROLES.get();
        return roles == null ? Collections.emptySet() : roles;
    }

    /**
     * 设置当前线程的权限集合.
     *
     * @param permissions 权限集合
     */
    public static void setPermissions(Collection<Permission> permissions) {
        PERMISSIONS.set(permissions == null ? Collections.emptySet() : new HashSet<>(permissions));
    }

    /**
     * 获取当前线程的权限集合.
     *
     * @return 权限集合
     */
    public static Set<Permission> getPermissions() {
        Set<Permission> permissions = PERMISSIONS.get();
        return permissions == null ? Collections.emptySet() : permissions;
    }

    /**
     * 清除当前线程的权限上下文.
     */
    public static void clear() {
        ROLES.remove();
        PERMISSIONS.remove();
    }
}
