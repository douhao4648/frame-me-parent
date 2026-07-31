package com.frame.me.auth.rbac.interceptor;

import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.auth.rbac.annotation.RequireAuth;
import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.auth.rbac.permission.AuthExpressionRoot;
import com.frame.me.auth.rbac.permission.AuthPermissionHolder;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * 权限拦截器.
 *
 * <p>仅对标注了 {@link RequireAuth} 的 Controller 方法生效：解析其 SpEL 表达式，
 * 调用 {@link AuthExpressionRoot} 中的 {@code role} / {@code perm} 函数进行权限校验。
 * 未登录访问受保护方法返回 401，已登录但无权限返回 403。
 * 未标注 {@link RequireAuth} 的方法不干预（登录校验交由 {@code AuthFilter}）。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final RbacProperties properties;
    private final IAuthPermissionProvider permissionProvider;
    private final IFilterErrorResponseWriter errorResponseWriter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // CORS 预检请求（OPTIONS）带 Origin 头时直接放行，不参与权限校验。
        // 与 AuthFilter 对齐：无 Origin 的 OPTIONS 非真预检，仍走正常权限链，防绕过。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Origin") != null) {
            return true;
        }
        if (!isPermissionEnabled() || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (isAnonymous(handlerMethod)) {
            return true;
        }

        // 先解析注解：无 @RequireAuth 的接口直接放行，避免无谓的权限加载（DB 查询）
        RequireAuth annotation = resolveAnnotation(handlerMethod);
        if (annotation == null) {
            return true;
        }

        User user = AuthContext.getUser();
        if (user == null) {
            // 声明了权限要求但未登录：401 未认证（语义上区别于 403 已认证无权限）
            AuthPermissionHolder.clear();
            writeError(response, ResultCode.UNAUTHORIZED);
            return false;
        }

        try {
            AuthPermissionHolder.ensureLoaded(user, permissionProvider);
        } catch (Exception e) {
            log.error("权限加载失败: {}", e.getMessage(), e);
            AuthPermissionHolder.clear();
            writeError(response, ResultCode.ERROR);
            return false;
        }

        if (AuthExpressionRoot.evaluate(annotation.value(), spelVariables(request))) {
            return true;
        }

        // 已登录但无权限：403。preHandle 返回 false 时 Spring 不会回调 afterCompletion，需自行清理
        AuthPermissionHolder.clear();
        try {
            writeError(response, ResultCode.FORBIDDEN);
        } catch (Exception ignored) {
            // writeError 失败时 ThreadLocal 已清理，忽略
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthPermissionHolder.clear();
    }

    private boolean isPermissionEnabled() {
        return Boolean.TRUE.equals(properties.getEnabled());
    }

    private boolean isAnonymous(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), Anonymous.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), Anonymous.class);
    }

    private RequireAuth resolveAnnotation(HandlerMethod handlerMethod) {
        RequireAuth annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequireAuth.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireAuth.class);
        }
        return annotation;
    }

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws Exception {
        errorResponseWriter.write(response, resultCode, null);
    }

    /**
     * 提取 SpEL 变量：查询参数（{@code @RequestParam}，含 POST 表单）与 URI 路径变量
     * （{@code @PathVariable}，如 {@code /api/order/{id}} 的 {@code id}），
     * 供 SpEL 以 {@code #变量名} 引用，例如 {@code @RequireAuth("dataCheck('order', #id)")}.
     *
     * <p>单值参数给 {@code String}，多值参数给 {@code String[]}。同名时<b>路径变量优先</b>：
     * 校验值必须与 {@code @PathVariable} 实际绑定值一致，否则 {@code /api/order/5?id=6}
     * 会出现"校验 6、执行 5"的越权窗口。</p>
     */
    private Map<String, Object> spelVariables(HttpServletRequest request) {
        Map<String, Object> variables = null;
        // 查询参数（GET query / POST form）。getParameterMap 正常不返回 null,判空兼容 mock 场景
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            variables = new HashMap<>();
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                String[] values = entry.getValue();
                if (values != null && values.length > 0) {
                    variables.put(entry.getKey(), values.length == 1 ? values[0] : values);
                }
            }
        }
        // 路径变量后放,覆盖同名查询参数(见 javadoc)
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> uriVariables && !uriVariables.isEmpty()) {
            if (variables == null) {
                variables = new HashMap<>();
            }
            for (Map.Entry<?, ?> entry : uriVariables.entrySet()) {
                variables.put((String) entry.getKey(), entry.getValue());
            }
        }
        return variables;
    }
}
