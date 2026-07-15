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
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

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

        AuthPermissionHolder.ensureLoaded(user, permissionProvider);

        if (AuthExpressionRoot.evaluate(annotation.value())) {
            return true;
        }

        // 已登录但无权限：403。preHandle 返回 false 时 Spring 不会回调 afterCompletion，需自行清理
        AuthPermissionHolder.clear();
        writeError(response, ResultCode.FORBIDDEN);
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

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws Exception {
        errorResponseWriter.write(response, resultCode, null);
    }
}
