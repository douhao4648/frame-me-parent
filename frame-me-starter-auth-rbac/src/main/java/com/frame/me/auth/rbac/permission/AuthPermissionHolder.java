package com.frame.me.auth.rbac.permission;

import com.frame.me.base.user.User;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 认证权限线程本地持有器.
 *
 * <p>缓存当前请求解析出的角色、权限和数据权限，避免同一请求多次调用 {@link IAuthPermissionProvider}。
 * 通过独立的 {@code loaded} 标志判断是否已加载，使"合法的空角色/空权限用户"不会被重复加载。</p>
 *
 * @author frame-me
 */
public class AuthPermissionHolder {

    private static final ThreadLocal<Set<String>> ROLES = new ThreadLocal<>();
    private static final ThreadLocal<Set<Permission>> PERMISSIONS = new ThreadLocal<>();
    private static final ThreadLocal<Set<DataPermission>> DATA_PERMISSIONS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> LOADED = new ThreadLocal<>();

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
     * 设置当前线程的数据权限集合.
     *
     * @param dataPermissions 数据权限集合
     */
    public static void setDataPermissions(Collection<DataPermission> dataPermissions) {
        DATA_PERMISSIONS.set(dataPermissions == null ? Collections.emptySet() : new HashSet<>(dataPermissions));
    }

    /**
     * 获取当前线程的数据权限集合.
     *
     * @return 数据权限集合
     */
    public static Set<DataPermission> getDataPermissions() {
        Set<DataPermission> dataPermissions = DATA_PERMISSIONS.get();
        return dataPermissions == null ? Collections.emptySet() : dataPermissions;
    }

    /**
     * 判断当前线程是否已完成权限加载（即使结果为空）.
     *
     * @return 是否已加载
     */
    public static boolean isLoaded() {
        return Boolean.TRUE.equals(LOADED.get());
    }

    /**
     * 标记当前线程已完成权限加载（用于异步上下文传播时恢复 loaded 状态）.
     */
    public static void markLoaded() {
        LOADED.set(Boolean.TRUE);
    }

    /**
     * 确保当前线程已加载角色与权限.
     *
     * <p>同一请求内只调用一次 {@link IAuthPermissionProvider}，即使该用户没有任何角色/权限。
     * Filter 与 Interceptor 共用本方法，避免重复实现。</p>
     *
     * @param user     当前用户
     * @param provider 权限提供者
     */
    public static void ensureLoaded(User user, IAuthPermissionProvider provider) {
        if (isLoaded()) {
            return;
        }
        setRoles(provider.getRoles(user));
        setPermissions(provider.getPermissions(user));
        setDataPermissions(provider.getDataPermissions(user));
        LOADED.set(Boolean.TRUE);
    }

    /**
     * 清除当前线程的权限上下文.
     */
    public static void clear() {
        ROLES.remove();
        PERMISSIONS.remove();
        DATA_PERMISSIONS.remove();
        LOADED.remove();
    }
}
