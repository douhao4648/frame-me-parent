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
     * 服务间调用时认证信息传播配置.
     */
    private Propagate propagate = new Propagate();

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
