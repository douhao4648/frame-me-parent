package com.frame.me.gateway.auth;

import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * 二三方应用认证器（app 路由）.
 *
 * @author frame-me
 */
public interface IAppAuthenticator {

    /**
     * 校验请求的应用身份.
     *
     * @param request       当前请求
     * @param authenticated 认证通过后执行的操作；须在返回的 Mono 完成前消费缓存 body
     * @return 操作结果，非法返回 {@code Mono.empty()}（由 filter 统一 401）
     */
    <T> Mono<T> authenticate(ServerHttpRequest request, Function<AppAuthResult, Mono<T>> authenticated);

    /**
     * app 认证结果：appKey + 可选的已校验请求体缓存.
     *
     * <p>{@code cachedBody} 仅在做过请求体摘要校验（digest opt-in）时非 null：
     * body 流已在认证阶段被消费，filter 必须从缓存完整重放。缓存仅在 authenticated 操作内
     * 有效，完成、出错或取消后由认证器释放。</p>
     */
    record AppAuthResult(String appKey, CachedRequestBody cachedBody) {

        /**
         * 未做 body 校验的结果（默认路径，body 流未被触碰）.
         */
        public static AppAuthResult withoutBody(String appKey) {
            return new AppAuthResult(appKey, null);
        }
    }
}
