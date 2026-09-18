package com.frame.me.gateway.filter;

import com.frame.me.gateway.auth.IAppAuthenticator;
import com.frame.me.gateway.auth.IUserValidator;
import com.frame.me.gateway.config.GatewayAuthProperties;
import com.frame.me.gateway.config.GatewayConstant;
import com.frame.me.gateway.error.GatewayGlobalExceptionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 网关鉴权全局过滤器：凭证驱动分发（user/app/无凭证），统一身份头契约.
 *
 * <p>不按路由声明鉴权类型，按请求携带的凭证自动识别：
 * {@code Authorization} 以 {@code "Signature "} 开头（显式 app 意图，优先于用户凭证）走 app 验签；
 * 否则提取用户 token 走 user 校验；无凭证按 {@code me.gateway.auth.allow-anonymous}
 * （false=401，true=剥离身份头后匿名放行）。所有分支验失败即 401，不落回。</p>
 *
 * <p>所有请求（含白名单与 allow-anonymous=true）无条件剥离外部身份头（防伪造）；
 * user 认证通过注入 {@code X-User-Id}（及 {@code X-User-Account}），app 验签通过注入
 * {@code X-App-Key}，下游以 {@code me.auth.trusted-header.enabled=true} 信任。</p>
 *
 * <p>实例级开关：{@code user-auth-enabled}/{@code app-auth-enabled} 关闭后对应认证器
 * 不装配，携带该类凭证直接 401（不落回、不 NPE）。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private final GatewayAuthProperties properties;
    private final ObjectProvider<IUserValidator> userValidatorProvider;
    private final ObjectProvider<IAppAuthenticator> appAuthenticatorProvider;
    private final ObjectMapper objectMapper;

    /**
     * 剥离 token 前缀（大小写不敏感，RFC 6750），空前缀原样返回.
     */
    private static String stripPrefix(String value, String prefix) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (prefix != null && !prefix.isEmpty()) {
            String p = prefix.trim();
            if (trimmed.regionMatches(true, 0, p, 0, p.length())) {
                return trimmed.substring(p.length()).trim();
            }
        }
        return trimmed;
    }

    @Override
    public int getOrder() {
        // 在路由转发（RouteToRequestUrlFilter）之前完成鉴权与身份头改写
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (isWhitelisted(request.getPath().value())) {
            return chain.filter(stripIdentityHeaders(exchange));
        }
        // Authorization: Signature 是显式 app 意图，优先于用户凭证（sa-token 模式下两类头并存时走 app）
        String authz = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authz != null && authz.startsWith(GatewayConstant.APP_AUTH_SCHEME)) {
            IAppAuthenticator authenticator = appAuthenticatorProvider.getIfAvailable();
            if (authenticator == null) {
                return unauthorized(exchange, "app credential not accepted on this instance");
            }
            return filterApp(exchange, chain, authenticator);
        }
        String token = extractUserToken(request);
        if (token != null) {
            IUserValidator validator = userValidatorProvider.getIfAvailable();
            if (validator == null) {
                return unauthorized(exchange, "user credential not accepted on this instance");
            }
            return filterUser(exchange, chain, validator, token);
        }
        // 无凭证：allow-anonymous=false（默认）401 要求认证；true 剥离身份头后匿名放行
        if (properties.isAllowAnonymous()) {
            return chain.filter(stripIdentityHeaders(exchange));
        }
        return unauthorized(exchange, "missing credential");
    }

    private boolean isWhitelisted(String path) {
        org.springframework.util.AntPathMatcher matcher = new org.springframework.util.AntPathMatcher();
        return properties.getWhitelist().stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    /**
     * 用户凭证：校验 token → 剥离身份头 → 注入已认证身份.
     */
    private Mono<Void> filterUser(ServerWebExchange exchange, GatewayFilterChain chain,
                                  IUserValidator userValidator, String token) {
        return userValidator.validate(token)
                .flatMap(identity -> {
                    ServerWebExchange mutated = stripIdentityHeaders(exchange);
                    ServerHttpRequest request = mutated.getRequest().mutate()
                            .header(GatewayConstant.HEADER_USER_ID, identity.userId())
                            .headers(headers -> {
                                if (identity.account() != null) {
                                    headers.set(GatewayConstant.HEADER_USER_ACCOUNT, identity.account());
                                }
                            })
                            .build();
                    return chain.filter(mutated.mutate().request(request).build());
                })
                .switchIfEmpty(Mono.defer(() -> unauthorized(exchange, "invalid or expired token")));
    }

    /**
     * app 凭证：HMAC 签名验证 → 剥离身份头 → 注入 appKey.
     */
    private Mono<Void> filterApp(ServerWebExchange exchange, GatewayFilterChain chain,
                                 IAppAuthenticator appAuthenticator) {
        return appAuthenticator.authenticate(exchange.getRequest())
                .flatMap(appKey -> {
                    ServerWebExchange mutated = stripIdentityHeaders(exchange);
                    ServerHttpRequest request = mutated.getRequest().mutate()
                            .header(GatewayConstant.HEADER_APP_KEY, appKey)
                            .build();
                    return chain.filter(mutated.mutate().request(request).build());
                })
                .switchIfEmpty(Mono.defer(() -> unauthorized(exchange, "invalid app credential")));
    }

    /**
     * 无条件剥离外部身份头（防伪造），认证分支在剥离后的 exchange 上重新注入.
     */
    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> GatewayConstant.STRIPPED_HEADERS.forEach(headers::remove))
                .build();
        return exchange.mutate().request(request).build();
    }

    /**
     * 提取用户 token：jwt 模式读配置的 header（默认 Authorization）+ 前缀剥离；
     * sa-token 模式先读 token-name 头，空则回退 Authorization Bearer.
     */
    private String extractUserToken(ServerHttpRequest request) {
        if (properties.getUserValidator() == GatewayAuthProperties.UserValidatorType.SA_TOKEN) {
            GatewayAuthProperties.SaToken st = properties.getSaToken();
            String value = request.getHeaders().getFirst(st.getTokenName());
            if (value != null && !value.isBlank()) {
                return stripPrefix(value, st.getTokenPrefix());
            }
            value = request.getHeaders().getFirst("Authorization");
            return stripPrefix(value, "Bearer ");
        }
        GatewayAuthProperties.Jwt jwt = properties.getJwt();
        return stripPrefix(request.getHeaders().getFirst(jwt.getTokenHeader()), jwt.getTokenPrefix());
    }

    /**
     * 统一 401：复用全局错误写出，与 base {@code IResult} 同构.
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return GatewayGlobalExceptionHandler.writeError(exchange, HttpStatus.UNAUTHORIZED, message, objectMapper)
                .then(Mono.fromRunnable(() ->
                        log.debug("网关鉴权拒绝: {} {}", exchange.getRequest().getMethod(),
                                exchange.getRequest().getPath().value())));
    }
}
