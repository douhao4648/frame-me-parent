package com.frame.me.auth.satoken.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sa-Token 认证配置属性（框架自有配置）.
 *
 * <p>sa-token 原生参数（{@code token-name} / {@code timeout} / {@code active-timeout} /
 * {@code is-concurrent} / {@code is-share} / {@code cookie.*} 等）请使用官方
 * {@code sa-token.*} 配置路径，由官方 starter 的 {@code SaBeanRegister} 绑定，
 * 本类只承载框架自有配置（{@code me.auth.sa-token.*}）。</p>
 *
 * @author frame-me
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "me.auth.sa-token")
public class SaTokenAuthProperties {

    /**
     * 是否启用 Sa-Token 认证，默认启用.
     */
    private boolean enabled = true;

    /**
     * 账号体系标识（sa-token 多账号 loginType），默认 {@code login}（即 {@code StpUtil} 默认体系）.
     *
     * <p>配置为非默认值（如 SSO 服务的 {@code sso}）时，本模块所有认证动作
     * （登录/登出/验 token/路径规则校验）改走 {@code SaManager.getStpLogic(logicType)}，
     * Redis key 变为 {@code {tokenName}:{logicType}:token|session:*}，
     * 与默认 {@code login} 体系共存、命名空间隔离；请求头/Cookie 名仍取全局
     * {@code sa-token.token-name}，不受影响。业务侧 {@code @SaCheck*} 注解需同步加
     * {@code type} 属性指向同一体系（装配期解析即完成 SaManager 注册，注解查找可用）。</p>
     *
     * <p><b>JWT 留口：</b>JWT 模式（{@code me.auth.sa-token.jwt.enabled}）下非默认体系
     * 仍会创建普通 {@code StpLogic}（非 JWT 变体），如需 JWT 化的独立体系须自行注册
     * {@code StpLogicJwtForSimple} 子类 Bean。</p>
     */
    private String logicType = "login";

    /**
     * Sa-Token 认证接口基础路径，默认 {@code /api/auth}.
     *
     * <p>必须以 {@code /} 开头，除根路径 {@code /} 外不能以 {@code /} 结尾。
     * 配置后，登录/登出/刷新/当前用户接口均会迁移到该路径下。</p>
     */
    private String path = "/api/auth";

    /**
     * Interceptor 层路径 → 简化鉴权表达式映射.
     *
     * <p>key 为 Ant 风格路径，value 为简化表达式（非 SpEL）：
     * <ul>
     *   <li>{@code login} —— 校验已登录</li>
     *   <li>{@code role:xxx} —— 校验拥有角色 xxx</li>
     *   <li>{@code perm:resource} —— 校验拥有 resource 权限码</li>
     *   <li>{@code perm:resource:action} —— 校验拥有 resource:action 权限码</li>
     * </ul>
     *
     * <p><b>注意：</b>Spring Boot 对 {@code Map} 的 key 做 relaxed binding 时，会剥离
     * 除字母数字、{@code -}、{@code .} 之外的字符，路径里的 {@code /} 和 {@code *}
     * 会被删掉（{@code /api/admin/**} 变成 {@code apiadmin}），导致规则静默失效（fail-open）。
     * 因此 YAML 中必须用方括号记法保留原始 key：
     * <pre>{@code
     * rules:
     *   "[/api/admin/**]": "role:admin"
     *   "[/api/order/**]": "perm:order:read"
     * }</pre>
     */
    private Map<String, String> rules = new LinkedHashMap<>();

    /**
     * 角色到权限的映射（sa-token 原生权限码格式，原样透传）.
     *
     * <p>key 为角色标识，value 为逗号分隔的权限码（{@code resource:action} 或 {@code resource}）。
     * 示例：
     * <pre>{@code
     * admin: "user:add,order:read"
     * }</pre>
     */
    private Map<String, String> roles = new HashMap<>();

    /**
     * 用户到角色的映射.
     *
     * <p>key 为用户 ID（字符串），value 为逗号分隔的角色标识。
     * 示例：
     * <pre>{@code
     * "1": "admin,operator"
     * }</pre>
     */
    private Map<String, String> users = new HashMap<>();

    /**
     * 会话绝对寿命上限（秒），默认 7 天（604800）；{@code <= 0} 表示不限制.
     *
     * <p>{@code refresh} 为滑动续期，不设上限时被偷的 token 可无限续命、会话永不过期。
     * 登录时在 Account-Session 记录登录时间戳，续期超过该上限即拒绝（4001），强制重新登录。</p>
     */
    private long maxLifetime = 604800;

    /**
     * 是否启用 sa-token 鉴权能力（路径规则 + {@code @SaCheck*} 注解），默认启用.
     *
     * <p>关闭后仍保留登录 / 登出 / 续期 / 强制登出 / 在线会话 / 踢人等认证与会话治理能力，
     * 仅不再注册 {@link cn.dev33.satoken.interceptor.SaInterceptor}。</p>
     */
    private Authorization authorization = new Authorization();

    /**
     * JWT Token 模式配置.
     */
    private Jwt jwt = new Jwt();

    /**
     * Redis 会话后端配置.
     */
    private Redis redis = new Redis();

    /**
     * 启动时校验规则 key：若不以 {@code /} 开头，多半是 YAML 中未用方括号记法，
     * 导致路径里的 {@code /}、{@code *} 被 relaxed binding 剥离，规则会静默失效（fail-open）。
     * 校验不通过直接抛异常，阻止应用启动。
     */
    @PostConstruct
    void validateRuleKeys() {
        for (String key : rules.keySet()) {
            if (!key.startsWith("/")) {
                throw new IllegalStateException(
                        "Sa-Token 路径规则 key [" + key + "] 不是以 '/' 开头的有效路径，规则不会生效。"
                                + "YAML 中 Map key 含 '/' 或 '*' 时会被 Spring Boot relaxed binding 剥离字符，"
                                + "请改用方括号记法：\"[/api/xxx/**]\"");
            }
        }
    }

    /**
     * sa-token 鉴权能力开关.
     */
    @Data
    public static class Authorization {

        /**
         * 是否启用 sa-token 鉴权（路径规则 + {@code @SaCheck*} 注解），默认 {@code true}.
         */
        private boolean enabled = true;
    }

    /**
     * JWT Token 模式配置.
     */
    @Data
    public static class Jwt {

        /**
         * 是否启用 JWT Token 模式，默认 {@code false}.
         *
         * <p>开启后登录颁发的 Token 会变为 JWT 格式，便于网关独立验签；
         * 仍使用 {@code StpLogicJwtForSimple}（Simple 模式），会话数据继续存 Redis，
         * 保留踢人 / 在线会话 / 强制登出等治理能力。</p>
         *
         * <p>启用后需在业务工程显式引入 {@code sa-token-jwt} 依赖，并配置
         * {@code sa-token.jwt-secret-key}。</p>
         */
        private boolean enabled = false;
    }

    /**
     * Redis 会话后端配置项.
     */
    @Data
    public static class Redis {

        /**
         * 是否启用 Redis 会话存储，默认 true（需 classpath 存在 frame-me-starter-multi-redis）.
         *
         * <p>multi-redis 缺席时本配置不生效，sa-token 退回内存 DAO（单实例可用）。</p>
         */
        private boolean enabled = true;

        /**
         * Redis 实例名（对应 {@code me.redis.clients} 的 key），默认 {@code default}.
         */
        private String clientName = "default";
    }
}
