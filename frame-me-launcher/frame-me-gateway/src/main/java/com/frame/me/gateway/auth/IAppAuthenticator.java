package com.frame.me.gateway.auth;

import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

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
     * @return 合法返回 appKey，非法返回 {@code Mono.empty()}（由 filter 统一 401）
     */
    Mono<String> authenticate(ServerHttpRequest request);
}
