package com.frame.me.auth.rbac.interceptor;

import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.rbac.annotation.RequireAuth;
import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.auth.core.AuthContext;
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
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * 权限拦截器.
 *
 * <p>解析 {@link RequireAuth} 上的 SpEL 表达式，调用 {@link AuthExpressionRoot} 中的
 * {@code role} / {@code perm} 函数进行权限校验，无权限时返回 403。</p>
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
        if (!isPermissionEnabled() || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (isAnonymous(handlerMethod)) {
            return true;
        }

        User user = AuthContext.getUser();
        if (user == null) {
            writeForbidden(response);
            return false;
        }

        ensurePermissionsLoaded(user);

        RequireAuth annotation = resolveAnnotation(handlerMethod);
        if (annotation == null) {
            return true;
        }

        if (AuthExpressionRoot.evaluate(annotation.value())) {
            return true;
        }

        writeForbidden(response);
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
        Method method = handlerMethod.getMethod();
        Class<?> beanType = handlerMethod.getBeanType();
        return method.getAnnotation(Anonymous.class) != null
                || beanType.getAnnotation(Anonymous.class) != null;
    }

    private RequireAuth resolveAnnotation(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        RequireAuth annotation = method.getAnnotation(RequireAuth.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireAuth.class);
        }
        return annotation;
    }

    private void ensurePermissionsLoaded(User user) {
        if (!AuthPermissionHolder.getRoles().isEmpty() || !AuthPermissionHolder.getPermissions().isEmpty()) {
            return;
        }
        AuthPermissionHolder.setRoles(permissionProvider.getRoles(user));
        AuthPermissionHolder.setPermissions(permissionProvider.getPermissions(user));
    }

    private void writeForbidden(HttpServletResponse response) throws Exception {
        errorResponseWriter.write(response, ResultCode.FORBIDDEN, null);
    }
}
