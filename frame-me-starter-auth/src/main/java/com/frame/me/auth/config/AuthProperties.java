package com.frame.me.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证模块配置属性.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.auth")
public class AuthProperties {

    /**
     * 是否启用认证模块，默认启用.
     */
    private Boolean enabled = true;

    /**
     * 是否强制要求登录，默认 {@code true}.
     *
     * <p>关闭后，非白名单请求未解析到用户时也不会返回 401，过滤器仅尝试解析用户并写入
     * {@link com.frame.me.auth.core.AuthContext}；适合网关已做认证、下游只取用户不强制登录的场景。</p>
     */
    private Boolean enforceLogin = true;

    /**
     * 匿名访问时是否跳过用户解析，默认 {@code false}.
     *
     * <p>为 {@code true} 时，匿名接口不会把请求头中的用户写入上下文。</p>
     */
    private Boolean skipAnonymousContext = false;

    /**
     * 白名单路径列表，支持 Ant 风格通配符.
     *
     * <p>例如：{@code /api/auth/login}、{@code /swagger-ui/**}。</p>
     */
    private List<String> whitelist = new ArrayList<>();

    /**
     * 管理员接口开关配置.
     */
    private Admin admin = new Admin();

    /**
     * 基于请求头的默认用户解析器开关配置.
     */
    private HeaderResolver headerResolver = new HeaderResolver();

    /**
     * 服务间调用时认证信息传播配置.
     */
    private Propagate propagate = new Propagate();

    /**
     * 管理员接口开关配置.
     */
    @Data
    public static class Admin {

        /**
         * 是否启用管理员强制登出接口，默认 {@code false}.
         *
         * <p>该接口默认不做权限校验，启用后业务方必须通过 sa-token 路径规则、RBAC 规则或自定义拦截器
         * 自行保护，避免任意已登录/匿名用户可踢掉他人。</p>
         */
        private Boolean logoutEnabled = false;
    }

    /**
     * 基于请求头的默认用户解析器开关配置.
     *
     * <p>该解析器无条件信任客户端传入的 {@code X-User-Id} 请求头，
     * 仅适用于不直接对外暴露的内网服务间调用场景，因此默认关闭、需显式开启。</p>
     */
    @Data
    public static class HeaderResolver {

        /**
         * 是否启用基于请求头的默认用户解析器，默认 {@code false}.
         *
         * <p>仅当服务不直接对外暴露（前置网关已剥离外部请求的 {@code X-User-Id} 头）、
         * 且调用方均为内网可信服务时才应开启。对外应用应引入
         * {@code frame-me-starter-auth-jwt} 或 {@code frame-me-starter-auth-sa-token}
         * 提供真实的 {@code IAuthUserResolver} 实现。</p>
         */
        private Boolean enabled = false;
    }

    /**
     * 认证信息传播配置.
     */
    @Data
    public static class Propagate {

        /**
         * 是否启用认证信息传播，默认启用.
         */
        private Boolean enabled = true;

        /**
         * 异步线程认证上下文传播配置.
         */
        private Async async = new Async();

        /**
         * 需要从当前请求原样传播到下游的请求头列表.
         *
         * <p>默认包含 {@code Authorization}（JWT）和 {@code X-User-Id} / {@code X-User-Account}
         * （header-auth），覆盖两类认证方式。</p>
         */
        private List<String> headers = List.of("Authorization", "X-User-Id", "X-User-Account");

        /**
         * 允许传播认证头的目标主机白名单，默认空列表（不限制，保持兼容）.
         *
         * <p>支持精确主机名（不区分大小写）、{@code *.example.com} 后缀通配以及 {@code *} 全匹配。
         * 配置后仅当出站请求的目标主机命中白名单时才注入认证头，
         * 防止 {@code Authorization} / {@code X-User-Id} 泄漏给外部第三方地址。</p>
         */
        private List<String> allowedHosts = new ArrayList<>();

        /**
         * 注册中心服务名调用甄别配置.
         */
        private ServiceDiscovery serviceDiscovery = new ServiceDiscovery();

        /**
         * 注册中心服务名调用甄别配置.
         *
         * <p>开启后，目标主机被甄别为注册中心服务名（Spring Cloud LoadBalancer 可解析）
         * 或单标签内网主机名（不含 {@code .}，如 {@code order-service}、{@code localhost}）时，
         * 即使不在 {@code allowed-hosts} 白名单内也允许传播认证头——内部服务名调用天然可信，
         * 外部域名/IP 默认不传播。</p>
         */
        @Data
        public static class ServiceDiscovery {

            /**
             * 是否允许向甄别为服务名调用的目标传播认证头，默认 {@code true}.
             *
             * <p>关闭后仅严格按 {@code allowed-hosts} 白名单传播。</p>
             */
            private Boolean enabled = true;
        }

        /**
         * 是否从 {@link com.frame.me.auth.core.AuthContext} 补充用户头.
         *
         * <p>开启后，若当前请求已登录，会把用户 ID 和账号作为 {@code X-User-Id} / {@code X-User-Account}
         * 写入出站请求，使 JWT 上游调用 header-auth 下游时也能被识别。</p>
         */
        private UserInfo userInfo = new UserInfo();

        /**
         * 从认证上下文补充用户头的配置.
         */
        @Data
        public static class UserInfo {

            /**
             * 是否启用，默认启用.
             */
            private Boolean enabled = true;

            /**
             * 用户 ID 头名，默认 {@code X-User-Id}.
             */
            private String userIdHeader = "X-User-Id";

            /**
             * 用户账号头名，默认 {@code X-User-Account}.
             */
            private String userAccountHeader = "X-User-Account";
        }

        /**
         * 异步线程认证上下文传播配置.
         */
        @Data
        public static class Async {

            /**
             * 是否启用异步线程认证上下文传播，默认启用.
             *
             * <p>开启后，默认 {@code @Async} 线程池的 {@link org.springframework.core.task.TaskDecorator}
             * 会捕获当前线程的 {@link com.frame.me.auth.core.AuthContext} 用户以及当前请求的认证头，
             * 并在异步线程执行前恢复。</p>
             */
            private Boolean enabled = true;
        }
    }
}
