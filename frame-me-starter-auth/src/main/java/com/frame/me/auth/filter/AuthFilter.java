package com.frame.me.auth.filter;

import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;

/**
 * 认证过滤器.
 *
 * <p>职责：
 * <ol>
 *   <li>白名单接口直接放行（不强制认证）</li>
 *   <li>非白名单接口未登录时直接返回 401</li>
 *   <li>已登录请求解析当前用户并写入 {@link AuthContext}</li>
 * </ol>
 * </p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class AuthFilter implements Filter {

    private final IAuthUserResolver userResolver;
    private final RequestMappingHandlerMapping handlerMapping;
    private final AuthProperties properties;
    private final IFilterErrorResponseWriter errorResponseWriter;

    private final PathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            if (isAnonymous(httpRequest)) {
                if (!Boolean.TRUE.equals(properties.getSkipAnonymousContext())) {
                    resolveAndSetUser(httpRequest);
                }
                chain.doFilter(request, response);
                return;
            }

            User user = resolveAndSetUser(httpRequest);
            if (user == null) {
                writeUnauthorized(httpResponse);
                return;
            }

            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    /**
     * 解析并设置当前用户，返回解析到的用户（可能为 null）.
     */
    private User resolveAndSetUser(HttpServletRequest request) {
        User user = userResolver.resolve(request);
        if (user != null) {
            AuthContext.setUser(user);
            log.debug("认证上下文已设置: userId={}, account={}", user.getId(), user.getAccount());
        }
        return user;
    }

    /**
     * 判断当前请求是否在白名单内.
     *
     * <p>白名单来源：
     * <ol>
     *   <li>{@code me.auth.whitelist} 配置的路径（Ant 风格通配符）</li>
     *   <li>Controller 类或方法上的 {@link Anonymous} 注解</li>
     * </ol>
     * </p>
     */
    private boolean isAnonymous(HttpServletRequest request) {
        String uri = request.getRequestURI();

        // 1. 配置白名单
        if (properties.getWhitelist() != null) {
            for (String pattern : properties.getWhitelist()) {
                if (pathMatcher.match(pattern, uri)) {
                    return true;
                }
            }
        }

        // 2. 注解白名单
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain == null) {
                return false;
            }
            Object handler = chain.getHandler();
            if (!(handler instanceof HandlerMethod handlerMethod)) {
                return false;
            }
            return AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), Anonymous.class)
                    || AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), Anonymous.class);
        } catch (Exception e) {
            log.warn("判断匿名接口时发生异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 写入 401 未授权响应.
     */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        errorResponseWriter.write(response, ResultCode.UNAUTHORIZED, null);
    }
}
