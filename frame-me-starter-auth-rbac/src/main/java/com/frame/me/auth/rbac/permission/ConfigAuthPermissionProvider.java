package com.frame.me.auth.rbac.permission;

import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.base.user.User;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于配置的权限提供者.
 *
 * <p>从 {@code me.auth.permission.*} 读取角色-权限映射和用户-角色映射，适合角色权限相对固定的场景。
 * 业务可以通过声明自定义 {@link IAuthPermissionProvider} bean 覆盖本实现，接入数据库或远程服务。</p>
 *
 * @author frame-me
 */
@RequiredArgsConstructor
public class ConfigAuthPermissionProvider implements IAuthPermissionProvider {

    private final RbacProperties properties;

    @Override
    public Collection<String> getRoles(User user) {
        if (user == null || user.getId() == null) {
            return Collections.emptyList();
        }
        Map<String, String> users = properties.getUsers();
        String rolesStr = users == null ? null : users.get(String.valueOf(user.getId()));
        if (rolesStr == null || rolesStr.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rolesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<Permission> getPermissions(User user) {
        Set<String> roleCodes = new HashSet<>(getRoles(user));
        if (roleCodes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, String> rolePermissions = properties.getRoles();
        if (rolePermissions == null || rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        return roleCodes.stream()
                .map(rolePermissions::get)
                .filter(s -> s != null && !s.isBlank())
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(String::trim)
                .filter(seg -> !seg.isEmpty())
                .map(this::parsePermission)
                .collect(Collectors.toSet());
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
