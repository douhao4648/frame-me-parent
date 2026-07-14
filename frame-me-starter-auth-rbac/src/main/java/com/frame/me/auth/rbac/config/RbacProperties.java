package com.frame.me.auth.rbac.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RBAC 权限控制配置属性.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.auth.permission")
public class RbacProperties {

    /**
     * 是否启用权限控制，默认启用.
     */
    private Boolean enabled = true;

    /**
     * Filter 层路径 → SpEL 表达式映射.
     *
     * <p>key 为 Ant 风格路径，value 为 SpEL 表达式。
     * 示例：
     * <pre>{@code
     * rules:
     *   /api/admin/** : "role('admin')"
     *   /api/order/** : "role('admin') and perm('order', 'r')"
     * }</pre>
     */
    private Map<String, String> rules = new LinkedHashMap<>();

    /**
     * 角色到权限的映射.
     *
     * <p>key 为角色标识，value 为逗号分隔的 {@code resource:action} 列表，
     * action 可省略（默认 {@code *}）。
     * 示例：
     * <pre>{@code
     * admin: "user:*,order,order:r"
     * }</pre>
     */
    private Map<String, String> roles = new HashMap<>();

    /**
     * 用户到角色的映射.
     *
     * <p>key 为用户 ID（字符串），value 为逗号分隔的角色标识。
     * 示例：
     * <pre>{@code
     * "1": "admin,saler"
     * }</pre>
     */
    private Map<String, String> users = new HashMap<>();
}
