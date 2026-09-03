package com.frame.me.gateway.filter;

import com.frame.me.gateway.config.GatewayAccessLogProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 网关访问日志过滤器（WebFilter，先于路由匹配）：每个入口请求完成后输出一行 INFO.
 *
 * <p>选 WebFilter 而非 GlobalFilter 的原因：GlobalFilter 只在路由命中时进入链，
 * 未命中路由的 404（探测、误配）不留痕；WebFilter 覆盖所有入口请求。
 * 字段：method / URI（含 query，按 {@code max-length} 截断）/ status / 耗时 / 远端地址。
 * 异常由外层 ErrorWebExceptionHandler 后置设置状态的路径，此处 status 可能为 {@code -}；
 * 以响应状态为准的精确统计归指标（prometheus profile），本日志面向链路排查。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class GatewayAccessLogFilter implements WebFilter, Ordered {

    private final GatewayAccessLogProperties properties;

    @Override
    public int getOrder() {
        // 最外层，包住鉴权/路由/转发全部分支
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startNanos = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        return chain.filter(exchange)
                .doFinally(signal -> logAccess(exchange, request, startNanos));
    }

    /**
     * 输出访问日志；URI 超长按 {@code max-length} 截断补 {@code ...}（0 不限制）.
     */
    private void logAccess(ServerWebExchange exchange, ServerHttpRequest request, long startNanos) {
        String target = request.getURI().getRawPath();
        String query = request.getURI().getRawQuery();
        if (query != null) {
            target = target + "?" + query;
        }
        int max = properties.getMaxLength();
        if (max > 0 && target.length() > max) {
            target = target.substring(0, max) + "...";
        }
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        long costMillis = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("log-access: method={}, uri={}, status={}, cost={}ms, remote={}",
                request.getMethod(),
                target,
                status == null ? "-" : status.value(),
                costMillis,
                request.getRemoteAddress() == null ? "-" : request.getRemoteAddress().getHostString());
    }
}
