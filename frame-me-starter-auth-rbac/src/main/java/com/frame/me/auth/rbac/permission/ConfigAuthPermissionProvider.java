package com.frame.me.auth.rbac.permission;

import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.base.user.User;
import jakarta.annotation.PostConstruct;
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
 * 角色-权限映射与数据权限在启动期（{@code @PostConstruct}）eager 解析并缓存，非法配置启动即 fail-fast。
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
     * 预解析后的角色 → 权限列表缓存，启动期由 {@link #init()} eager 解析.
     */
    private Map<String, List<Permission>> parsedRolePermissions = Collections.emptyMap();

    /**
     * 预解析后的角色 → 数据权限列表缓存，启动期由 {@link #init()} eager 解析.
     */
    private Map<String, List<DataPermission>> parsedRoleDataScopes = Collections.emptyMap();

    /**
     * 合法的数据范围取值（大写）.
     */
    private static final Set<String> VALID_SCOPES = Set.of(IDataScopes.ALL, IDataScopes.DEPT, IDataScopes.ORG,
            IDataScopes.SELF, IDataScopes.CUSTOM);

    /**
     * 已告警"未配置角色"的用户 ID（按 userId 去重，避免每个请求刷一条 WARN），有界防内存增长.
     */
    private static final int MAX_WARNED_USERS = 1000;
    private final Set<String> warnedNoRoleUsers = ConcurrentHashMap.newKeySet();

    /**
     * 启动期 eager 解析 {@code me.auth.permission.data-scopes} 与 {@code me.auth.permission.roles}.
     *
     * <p>格式非法（data-scopes 格式/scope 非法、roles 段 resource 为空）时抛 {@link IllegalStateException}
     * 直接 fail-fast——非法条目若只 WARN 跳过，该资源权限缺失意味着配置笔误被静默吞掉
     * （data-scopes 方向更是 fail-open 越权可见），启动期暴露远好于运行期排查。</p>
     */
    @PostConstruct
    public void init() {
        parsedRoleDataScopes = parseAllDataScopes(properties.getDataScopes());
        parsedRolePermissions = parseAll(properties.getRoles());
    }

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

        Map<String, List<Permission>> rolePermissions = parsedRolePermissions;
        if (rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        return roleCodes.stream()
                .map(rolePermissions::get)
                .filter(list -> list != null && !list.isEmpty())
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Collection<DataPermission> getDataPermissions(User user) {
        Set<String> roleCodes = new LinkedHashSet<>(getRoles(user));
        if (roleCodes.isEmpty() || parsedRoleDataScopes.isEmpty()) {
            return Collections.emptyList();
        }

        return roleCodes.stream()
                .map(parsedRoleDataScopes::get)
                .filter(list -> list != null && !list.isEmpty())
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
                                .map(seg -> parsePermission(e.getKey(), seg))
                                .collect(Collectors.toCollection(ArrayList::new))));
    }

    /**
     * 解析 {@code resource:action} 配置段，resource 为空直接 fail-fast（与 data-scopes 同标准）.
     */
    private Permission parsePermission(String role, String segment) {
        int colonIdx = segment.indexOf(':');
        String resource = colonIdx == -1 ? segment : segment.substring(0, colonIdx);
        String action = colonIdx == -1 ? "*" : segment.substring(colonIdx + 1);
        if (resource.isBlank()) {
            throw new IllegalStateException(
                    "me.auth.permission.roles[" + role + "] 配置段 [" + segment + "] resource 不能为空");
        }
        if (action.isEmpty()) {
            action = "*";
        }
        return new Permission(resource, action);
    }

    private Map<String, List<DataPermission>> parseAllDataScopes(Map<String, String> dataScopes) {
        if (dataScopes == null || dataScopes.isEmpty()) {
            return Collections.emptyMap();
        }
        return dataScopes.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Arrays.stream(e.getValue().split(","))
                                .map(String::trim)
                                .filter(seg -> !seg.isEmpty())
                                .map(seg -> parseDataPermission(e.getKey(), seg))
                                .collect(Collectors.toCollection(ArrayList::new))));
    }

    /**
     * 解析 {@code resource:SCOPE} 或 {@code resource:action:SCOPE} 配置段，非法段直接 fail-fast.
     */
    private DataPermission parseDataPermission(String role, String segment) {
        String[] parts = segment.split(":");
        String resource;
        String action;
        String scope;
        if (parts.length == 2) {
            resource = parts[0];
            action = "*";
            scope = parts[1];
        } else if (parts.length == 3) {
            resource = parts[0];
            action = parts[1].isEmpty() ? "*" : parts[1];
            scope = parts[2];
        } else {
            throw invalidDataScope(role, segment, "格式非法，应为 resource:SCOPE 或 resource:action:SCOPE");
        }
        if (resource.isBlank()) {
            throw invalidDataScope(role, segment, "resource 不能为空");
        }
        String upperScope = scope.toUpperCase();
        if (!VALID_SCOPES.contains(upperScope)) {
            throw invalidDataScope(role, segment, "SCOPE [" + scope + "] 非法，取值 ALL/DEPT/ORG/SELF/CUSTOM");
        }
        return new DataPermission(resource, action, upperScope, new LinkedHashSet<>());
    }

    private IllegalStateException invalidDataScope(String role, String segment, String reason) {
        return new IllegalStateException(
                "me.auth.permission.data-scopes[" + role + "] 配置段 [" + segment + "] " + reason);
    }
}
