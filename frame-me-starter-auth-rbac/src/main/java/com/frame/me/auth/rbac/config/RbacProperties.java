package com.frame.me.auth.rbac.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RBAC 权限控制配置属性.
 *
 * @author frame-me
 */
@Slf4j
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
     *
     * <p><b>注意：</b>Spring Boot 对 {@code Map} 的 key 做 relaxed binding 时，会剥离
     * 除字母数字、{@code -}、{@code .} 之外的字符，路径里的 {@code /} 和 {@code *}
     * 会被删掉（{@code /api/admin/**} 变成 {@code apiadmin}），导致规则静默失效（fail-open）。
     * 因此 YAML 中必须用方括号记法保留原始 key：
     * <pre>{@code
     * rules:
     *   "[/api/admin/**]": "role('admin')"
     *   "[/api/order/**]": "role('admin') and perm('order', 'r')"
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

    /**
     * 角色到数据范围的映射.
     *
     * <p>key 为角色标识，value 为逗号分隔的 {@code resource:SCOPE} 或 {@code resource:action:SCOPE},
     * SCOPE 取值为 {@code ALL}/{@code DEPT}/{@code ORG}/{@code SELF}/{@code CUSTOM}
     * （见 {@link com.frame.me.auth.rbac.permission.IDataScopes}）。
     * 格式或 SCOPE 非法时启动 fail-fast（{@code IllegalStateException}）——非法条目若静默跳过，
     * 该资源数据权限缺失意味着不加行级限制（fail-open 方向）。
     * 配置方式只能表达 scope 级别，动态 {@code dataIds} 需自定义
     * {@code IAuthPermissionProvider#getDataPermissions} 提供。
     * 示例：
     * <pre>{@code
     * admin: "order:ALL"
     * user: "order:SELF,order:read:CUSTOM"
     * }</pre>
     */
    private Map<String, String> dataScopes = new HashMap<>();

    /**
     * 启动时校验规则 key：若不以 {@code /} 开头，多半是 YAML 中未用方括号记法，
     * 导致路径里的 {@code /}、{@code *} 被 relaxed binding 剥离，规则会静默失效（fail-open）。
     */
    @PostConstruct
    void validateRuleKeys() {
        for (String key : rules.keySet()) {
            if (!key.startsWith("/")) {
                throw new IllegalStateException(
                        "权限规则 key [" + key + "] 不是以 '/' 开头的有效路径，规则不会生效。"
                        + "YAML 中 Map key 含 '/' 或 '*' 时会被 Spring Boot relaxed binding 剥离字符，"
                        + "请改用方括号记法：\"[/api/xxx/**]\"");
            }
        }
    }
}
