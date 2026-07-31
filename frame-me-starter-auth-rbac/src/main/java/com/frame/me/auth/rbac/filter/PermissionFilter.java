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

        // ERROR dispatch（容器 /error 转发）直接放行——与 AuthFilter 对齐，
        // 避免 404/servlet 级异常的真实状态码被 403 掩盖
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            try {
                chain.doFilter(request, response);
            } finally {
                AuthPermissionHolder.clear();
            }
            return;
        }

        // CORS 预检请求（OPTIONS）带 Origin 头时直接放行，不参与权限校验。
        // 与 AuthFilter 对齐：无 Origin 的 OPTIONS 非真预检，仍走正常权限链，防绕过。
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())
                && httpRequest.getHeader("Origin") != null) {
            try {
                chain.doFilter(request, response);
            } finally {
                AuthPermissionHolder.clear();
            }
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

            try {
                AuthPermissionHolder.ensureLoaded(user, permissionProvider);
            } catch (Exception e) {
                log.error("权限加载失败: {}", e.getMessage(), e);
                writeError(httpResponse, ResultCode.ERROR);
                return;
            }

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
        String contextPath = request.getContextPath();
        // 命中的多条规则取最具体的 pattern（AntPathMatcher 官方比较器：精确 > 单段通配 > **，
        // 最具体排最前，故取 min），避免 /api/** 在前把 /api/admin/** 降级为更宽松规则（fail-open）。
        // 不用字符串长度近似：长度相近时（如 /api/user-* 比 /api/users 更长）会选错.
        // 不改原 Map 顺序，仅本次匹配的局部排序.
        java.util.Comparator<String> specificity = pathMatcher.getPatternComparator(uri);
        return properties.getRules().entrySet().stream()
                .map(e -> Map.entry(ContextPathUtils.stripContextPath(e.getKey(), contextPath), e.getValue()))
                .filter(e -> pathMatcher.match(e.getKey(), uri))
                .min((a, b) -> specificity.compare(a.getKey(), b.getKey()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws IOException {
        errorResponseWriter.write(response, resultCode, null);
    }
}
