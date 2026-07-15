package com.frame.me.auth.rbac.permission;

import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.base.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于配置的权限提供者.
 *
 * <p>从 {@code me.auth.permission.*} 读取角色-权限映射和用户-角色映射，适合角色权限相对固定的场景。
 * 角色-权限映射在首次使用时预解析并缓存，避免每次请求重复 split/parse。
 * 业务可以通过声明自定义 {@link IAuthPermissionProvider} bean 覆盖本实现，接入数据库或远程服务；
 * 启用 Redis 后端时，作为数据源的自定义 bean 须命名为 {@code authPermissionSource}。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class ConfigAuthPermissionProvider implements IAuthPermissionProvider {

    private final RbacProperties properties;

    /**
     * 预解析后的角色 → 权限列表缓存（配置运行期不变，懒加载一次即可）.
     */
    private volatile Map<String, List<Permission>> parsedRolePermissions;

    /**
     * 已告警"未配置角色"的用户 ID（按 userId 去重，避免每个请求刷一条 WARN），有界防内存增长.
     */
    private static final int MAX_WARNED_USERS = 1000;
    private final Set<String> warnedNoRoleUsers = ConcurrentHashMap.newKeySet();

    @Override
    public Collection<String> getRoles(User user) {
        if (user == null || user.getId() == null) {
            return Collections.emptyList();
        }
        Map<String, String> users = properties.getUsers();
        String rolesStr = users == null ? null : users.get(String.valueOf(user.getId()));
        if (rolesStr == null || rolesStr.isBlank()) {
            if (users != null && !users.isEmpty()) {
                String uid = String.valueOf(user.getId());
                if (warnedNoRoleUsers.size() < MAX_WARNED_USERS && warnedNoRoleUsers.add(uid)) {
                    log.warn("未在 me.auth.permission.users 中找到用户 {} 的角色配置，按无角色处理（该用户后续不再重复告警）", user.getId());
                }
            }
            return Collections.emptyList();
        }
        return Arrays.stream(rolesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Collection<Permission> getPermissions(User user) {
        Set<String> roleCodes = new LinkedHashSet<>(getRoles(user));
        if (roleCodes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<Permission>> rolePermissions = parsedRolePermissions();
        if (rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        return roleCodes.stream()
                .map(rolePermissions::get)
                .filter(list -> list != null && !list.isEmpty())
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 预解析 {@code me.auth.permission.roles} 为 {@code 角色 -> 权限列表}，懒加载并缓存.
     */
    private Map<String, List<Permission>> parsedRolePermissions() {
        Map<String, List<Permission>> result = parsedRolePermissions;
        if (result == null) {
            synchronized (this) {
                result = parsedRolePermissions;
                if (result == null) {
                    result = parseAll(properties.getRoles());
                    parsedRolePermissions = result;
                }
            }
        }
        return result;
    }

    private Map<String, List<Permission>> parseAll(Map<String, String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyMap();
        }
        return roles.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Arrays.stream(e.getValue().split(","))
                                .map(String::trim)
                                .filter(seg -> !seg.isEmpty())
                                .map(this::parsePermission)
                                .collect(Collectors.toCollection(ArrayList::new))));
    }

    private Permission parsePermission(String segment) {
        int colonIdx = segment.indexOf(':');
        if (colonIdx == -1) {
            return new Permission(segment, "*");
        }
        String resource = segment.substring(0, colonIdx);
        String action = segment.substring(colonIdx + 1);
        if (action.isEmpty()) {
            action = "*";
        }
        return new Permission(resource, action);
    }
}
