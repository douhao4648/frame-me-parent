package com.frame.me.auth.propagation;

import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.base.user.User;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.List;

/**
 * 认证信息传播拦截器.
 *
 * <p>用于 {@code @ImportHttpServices} 声明式 HTTP 客户端，把当前请求的认证头传播到下游服务。
 * 默认同时传播 {@code Authorization}（JWT）和 {@code X-User-Id} / {@code X-User-Account}
 * （header-auth），并且会从 {@link AuthContext} 补充当前用户信息，使 JWT 上游调用
 * header-auth 下游时也能被识别。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class AuthPropagationInterceptor implements ClientHttpRequestInterceptor {

    private final AuthProperties properties;

    @Override
    @Nonnull
    public ClientHttpResponse intercept(@Nonnull HttpRequest request, @Nonnull byte[] body,
                                        @Nonnull ClientHttpRequestExecution execution) throws IOException {
        if (Boolean.FALSE.equals(properties.getPropagate().getEnabled())) {
            return execution.execute(request, body);
        }

        propagateHeaders(request);
        propagateUserInfoFromContext(request);

        return execution.execute(request, body);
    }

    /**
     * 从当前请求原样传播配置的请求头.
     */
    private void propagateHeaders(HttpRequest request) {
        List<String> headers = properties.getPropagate().getHeaders();
        if (headers == null || headers.isEmpty()) {
            return;
        }

        for (String headerName : headers) {
            if (headerName == null || headerName.isEmpty()) {
                continue;
            }
            if (request.getHeaders().getFirst(headerName) != null) {
                continue;
            }
            String headerValue = getCurrentRequestHeader(headerName);
            if (headerValue != null && !headerValue.isEmpty()) {
                request.getHeaders().add(headerName, headerValue);
                log.debug("认证头已传播到下游: headerName={}", headerName);
            }
        }
    }

    /**
     * 从 {@link AuthContext} 补充用户 ID / 账号头.
     *
     * <p>当上游是 JWT、下游是 header-auth 时，原请求可能只有 {@code Authorization} 头，
     * 此时从上下文取出用户并写入 {@code X-User-Id} / {@code X-User-Account}，让下游的
     * {@code HeaderAuthUserResolver} 能够识别。</p>
     */
    private void propagateUserInfoFromContext(HttpRequest request) {
        AuthProperties.Propagate.UserInfo userInfo = properties.getPropagate().getUserInfo();
        if (Boolean.FALSE.equals(userInfo.getEnabled())) {
            return;
        }

        User user = AuthContext.getUser();
        if (user == null) {
            return;
        }

        addHeaderIfAbsent(request, userInfo.getUserIdHeader(), user.getId() == null ? null : String.valueOf(user.getId()));
        addHeaderIfAbsent(request, userInfo.getUserAccountHeader(), user.getAccount());
    }

    /**
     * 添加头到出站请求，若已存在则不覆盖.
     */
    private void addHeaderIfAbsent(HttpRequest request, String headerName, String headerValue) {
        if (headerName == null || headerName.isEmpty() || headerValue == null || headerValue.isEmpty()) {
            return;
        }
        if (request.getHeaders().getFirst(headerName) != null) {
            return;
        }
        request.getHeaders().add(headerName, headerValue);
        log.debug("用户信息头已传播到下游: headerName={}", headerName);
    }

    /**
     * 从当前请求上下文读取指定头.
     *
     * <p>优先从 {@link RequestContextHolder} 绑定的 {@code HttpServletRequest} 读取；
     * 若当前线程无请求绑定（如 {@code @Async} 线程），则从 {@link AuthPropagationHolder} 读取
     * 由 {@link AuthContextTaskDecorator} 捕获的头。</p>
     *
     * @param headerName 头名称
     * @return 头值，不存在时返回 {@code null}
     */
    private String getCurrentRequestHeader(String headerName) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            String value = servletAttributes.getRequest().getHeader(headerName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return AuthPropagationHolder.getHeader(headerName);
    }
}
