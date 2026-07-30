package com.frame.me.auth.rbac.filter;

import com.frame.me.auth.core.AuthContext;
import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.auth.rbac.permission.AuthExpressionRoot;
import com.frame.me.auth.rbac.permission.AuthPermissionHolder;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.util.ContextPathUtils;
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
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Map;

/**
 * 权限过滤器.
 *
 * <p>按 {@code me.auth.permission.rules} 配置的路径→SpEL 表达式进行粗粒度权限校验。
 * 执行顺序在 AuthFilter 之后：命中规则但未登录返回 401，已登录但表达式求值为 false 返回 403。
 * 未命中规则的路径不干预。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class PermissionFilter implements Filter {

    private final RbacProperties properties;
    private final IAuthPermissionProvider permissionProvider;
    private final IFilterErrorResponseWriter errorResponseWriter;

    private final PathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // CORS 预检请求（OPTIONS）直接放行，不参与权限校验
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        try {
            if (!isPermissionEnabled()) {
                chain.doFilter(request, response);
                return;
            }

            String expr = findExpr(httpRequest);
            if (expr == null) {
                chain.doFilter(request, response);
                return;
            }

            User user = AuthContext.getUser();
            if (user == null) {
                // 命中权限规则但未登录：401 未认证
                writeError(httpResponse, ResultCode.UNAUTHORIZED);
                return;
            }

            AuthPermissionHolder.ensureLoaded(user, permissionProvider);

            if (AuthExpressionRoot.evaluate(expr)) {
                chain.doFilter(request, response);
            } else {
                writeError(httpResponse, ResultCode.FORBIDDEN);
            }
        } finally {
            AuthPermissionHolder.clear();
        }
    }

    private boolean isPermissionEnabled() {
        return Boolean.TRUE.equals(properties.getEnabled());
    }

    private String findExpr(HttpServletRequest request) {
        // 使用应用内路径匹配：getRequestURI() 含 context-path，
        // 配置 server.servlet.context-path 后会导致规则全部失配（静默放行，fail-open）
        String uri = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        for (Map.Entry<String, String> entry : properties.getRules().entrySet()) {
            // 平滑兼容误带 context-path 前缀的规则写法
            String pattern = ContextPathUtils.stripContextPath(entry.getKey(), request.getContextPath());
            if (pathMatcher.match(pattern, uri)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws IOException {
        errorResponseWriter.write(response, resultCode, null);
    }
}
