package com.frame.me.auth.filter;

import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.auth.util.ContextPathUtils;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import jakarta.servlet.DispatcherType;
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
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;

/**
 * 认证过滤器.
 *
 * <p>职责：
 * <ol>
 *   <li>白名单接口直接放行（不强制认证）</li>
 *   <li>{@code me.auth.enforce-login=false} 时仅尝试解析用户并写入 {@link AuthContext}，不返回 401</li>
 *   <li>默认情况下非白名单接口未登录时直接返回 401</li>
 *   <li>已登录请求解析当前用户并写入 {@link AuthContext}</li>
 *   <li>ERROR dispatch（容器 {@code /error} 转发）直接放行，避免 404/servlet 级异常的真实状态码被 401 掩盖</li>
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

        // ERROR dispatch（容器 /error 转发）直接放行：
        // 不过滤可让 404/servlet 级异常的真实状态码不被 401 掩盖。
        // 防御性 clear：正常场景 REQUEST dispatch 的 finally 已清理 AuthContext，
        // 但若 ERROR 路径上有其他组件调了 setUser，此处兜底防 ThreadLocal 残留（线程复用泄漏）
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            try {
                chain.doFilter(request, response);
            } finally {
                AuthContext.clear();
            }
            return;
        }

        // CORS 预检请求（OPTIONS）带 Origin 头时直接放行，不参与认证：
        // 预检由 CorsFilter（若启用）在更早优先级处理；此处为兜底，
        // 避免业务自定义 filter 顺序或 @CrossOrigin 场景下预检被认证拦截返回 401。
        // 要求 Origin 头：无 Origin 的 OPTIONS 非真预检，仍走正常鉴权链，防绕过
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())
                && httpRequest.getHeader("Origin") != null) {
            try {
                chain.doFilter(request, response);
            } finally {
                AuthContext.clear();
            }
            return;
        }

        try {
            if (isAnonymous(httpRequest)) {
                if (!Boolean.TRUE.equals(properties.getSkipAnonymousContext())) {
                    resolveAndSetUser(httpRequest);
                }
                chain.doFilter(request, response);
                return;
            }

            if (!Boolean.TRUE.equals(properties.getEnforceLogin())) {
                // 不强制登录：尝试解析用户并放行，不返回 401
                resolveAndSetUser(httpRequest);
                chain.doFilter(request, response);
                return;
            }

            User user = resolveAndSetUser(httpRequest);
            if (user == null) {
                // 拒绝必留痕：最高频的 401 路径，无日志则无法定位是哪一层拒绝的
                log.warn("认证失败，拒绝请求: method={}, uri={}", httpRequest.getMethod(), httpRequest.getRequestURI());
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
    private boolean isAnonymous(HttpServletRequest request) throws ServletException {
        // 使用应用内路径匹配：getRequestURI() 含 context-path，
        // 配置 server.servlet.context-path 后会导致白名单全部失配
        String uri = UrlPathHelper.defaultInstance.getPathWithinApplication(request);

        // 1. 配置白名单（平滑兼容误带 context-path 前缀的写法）
        if (properties.getWhitelist() != null) {
            for (String pattern : properties.getWhitelist()) {
                String appPattern = ContextPathUtils.stripContextPath(pattern, request.getContextPath());
                if (pathMatcher.match(appPattern, uri)) {
                    return true;
                }
            }
        }

        // 2. 注解白名单
        if (handlerMapping == null) {
            return false;
        }
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
        } catch (ServletException e) {
            // HandlerMapping 框架级异常（如请求分发失败），上抛由容器处理，不吞成 false
            throw e;
        } catch (Exception e) {
            // handlerMapping.getHandler 声明 throws Exception，此处捕获所有非 ServletException 异常。
            // fallback 返回 false（非匿名，需走认证流程），安全侧偏好（fail-closed），防止异常时误放行。
            // 降级 WARN：此类异常不影响请求处理主流程，ERROR 会触发告警噪音。
            log.warn("判断匿名接口时发生异常，本次请求按非匿名处理: {}", e.getMessage(), e);
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
