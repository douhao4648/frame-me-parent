package com.frame.me.gateway.auth;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 二三方应用认证器（app 路由）.
 *
 * @author frame-me
 */
public interface IAppAuthenticator {

    /**
     * 校验请求的应用身份.
     *
     * @param request 当前请求
     * @return 合法返回 appKey，非法返回 {@code null}（由 filter 统一 401）
     */
    String authenticate(ServerHttpRequest request);
}
