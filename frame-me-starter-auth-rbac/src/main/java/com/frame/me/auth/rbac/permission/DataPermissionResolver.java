package com.frame.me.auth.rbac.permission;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据权限合并解析器.
 *
 * <p>多角色下同一资源可能命中多条 {@link DataPermission}，合并语义：
 * 任一 {@code ALL} → 全部放行；否则 scope 取并集、{@code dataIds} 取并集。
 * SpEL 函数与 {@link AuthDataPermissions} 统一委托本类，保证两种使用方式语义一致。</p>
 *
 * @author frame-me
 */
public class DataPermissionResolver {

    private DataPermissionResolver() {
    }

    /**
     * 从数据权限集合中筛选匹配指定资源（与操作）的条目.
     *
     * @param dataPermissions 数据权限集合
     * @param resource        资源标识，{@code null} 时返回空集
     * @param action          操作标识，{@code null} 时只按资源匹配
     * @return 命中的数据权限条目
     */
    public static Set<DataPermission> matching(Collection<DataPermission> dataPermissions, String resource, String action) {
        if (dataPermissions == null || dataPermissions.isEmpty() || resource == null) {
            return Collections.emptySet();
        }
        Set<DataPermission> result = new LinkedHashSet<>();
        for (DataPermission dp : dataPermissions) {
            if (dp == null) {
                continue;
            }
            boolean matched = action == null ? dp.matchesResource(resource) : dp.matches(resource, action);
            if (matched) {
                result.add(dp);
            }
        }
        return result;
    }

    /**
     * 合并结果是否包含 {@code ALL} 数据范围.
     */
    public static boolean isAll(Collection<DataPermission> dataPermissions, String resource, String action) {
        return matching(dataPermissions, resource, action).stream()
                .anyMatch(dp -> IDataScopes.ALL.equalsIgnoreCase(dp.getDataScope()));
    }

    /**
     * 合并后的 scope 并集.
     */
    public static Set<String> scopes(Collection<DataPermission> dataPermissions, String resource, String action) {
        Set<String> result = new LinkedHashSet<>();
        for (DataPermission dp : matching(dataPermissions, resource, action)) {
            if (dp.getDataScope() != null) {
                result.add(dp.getDataScope());
            }
        }
        return result;
    }

    /**
     * 合并后的数据 ID 并集（资源行主键集合）.
     */
    public static Set<Long> dataIds(Collection<DataPermission> dataPermissions, String resource, String action) {
        Set<Long> result = new LinkedHashSet<>();
        for (DataPermission dp : matching(dataPermissions, resource, action)) {
            if (dp.getDataIds() != null) {
                result.addAll(dp.getDataIds());
            }
        }
        return result;
    }

    /**
     * 校验单条数据是否可访问：命中 {@code ALL} 范围或数据 ID 在并集中.
     *
     * @param dataId 数据行主键，{@code null} 时仅 {@code ALL} 可通过
     */
    public static boolean check(Collection<DataPermission> dataPermissions, String resource, String action, Long dataId) {
        if (isAll(dataPermissions, resource, action)) {
            return true;
        }
        return dataId != null && dataIds(dataPermissions, resource, action).contains(dataId);
    }

    /**
     * 将 SpEL / 业务传入的数据 ID 统一转为 {@code Long}.
     *
     * <p>{@link Number} 直接强转，数字字符串解析，其余（含非法字符串）返回 {@code null}。</p>
     */
    public static Long toLong(Object dataId) {
        if (dataId == null) {
            return null;
        }
        if (dataId instanceof Number number) {
            return number.longValue();
        }
        if (dataId instanceof String str) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
