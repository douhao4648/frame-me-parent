package com.frame.me.auth.rbac.interceptor;

import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.auth.rbac.annotation.RequireAuth;
import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.auth.rbac.permission.AuthPermissionHolder;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.Permission;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionInterceptor} 单元测试.
 *
 * @author frame-me
 */
class PermissionInterceptorTest {

    private RbacProperties properties;
    private IAuthPermissionProvider permissionProvider;
    private IFilterErrorResponseWriter errorResponseWriter;
    private PermissionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new RbacProperties();
        permissionProvider = mock(IAuthPermissionProvider.class);
        errorResponseWriter = mock(IFilterErrorResponseWriter.class);
        interceptor = new PermissionInterceptor(properties, permissionProvider, errorResponseWriter);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        AuthPermissionHolder.clear();
    }

    @Test
    void testRolePass() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("admin"));

        assertTrue(preHandle("adminOnly"));
    }

    @Test
    void testRoleForbidden() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("operator"));

        assertFalse(preHandle("adminOnly"));
        // 已登录但无权限：403
        verify(errorResponseWriter).write(any(HttpServletResponse.class), eq(ResultCode.FORBIDDEN), isNull());
    }

    @Test
    void testRoleOrPass() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("operator"));

        assertTrue(preHandle("anyAdminOrOperator"));
    }

    @Test
    void testPermPass() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getPermissions(any())).thenReturn(List.of(new Permission("user", "r")));

        assertTrue(preHandle("readUser"));
    }

    @Test
    void testPermForbidden() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getPermissions(any())).thenReturn(Collections.emptyList());

        assertFalse(preHandle("readUser"));
    }

    @Test
    void testPermOrPass() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getPermissions(any())).thenReturn(List.of(new Permission("order", "r")));

        assertTrue(preHandle("readUserOrOrder"));
    }

    @Test
    void testUnauthenticatedReturns401() throws Exception {
        // 声明了权限要求但未登录：401 未认证
        assertFalse(preHandle("adminOnly"));
        verify(errorResponseWriter).write(any(HttpServletResponse.class), eq(ResultCode.UNAUTHORIZED), isNull());
    }

    @Test
    void testNoAnnotationPassesWithoutLoadingPermissions() throws Exception {
        AuthContext.setUser(createUser(1L));

        // 无 @RequireAuth：直接放行，且不触发权限加载（顺序修复回归）
        assertTrue(preHandle("noAnnotation"));
        verifyNoInteractions(permissionProvider);
    }

    @Test
    void testAnonymousPassesWithoutLogin() throws Exception {
        // @Anonymous 方法：未登录放行，不触发权限加载
        assertTrue(preHandle("publicEndpoint"));
        verifyNoInteractions(permissionProvider);
    }

    @Test
    void testClassLevelRequireAuthForbidden() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("operator"));

        // 类级 @RequireAuth：方法未标注也生效
        assertFalse(classLevelPreHandle());
    }

    @Test
    void testClassLevelRequireAuthPass() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("admin"));

        assertTrue(classLevelPreHandle());
    }

    @Test
    void testInvalidSpelReturns403() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("admin"));

        // SpEL 语法错误：evaluate 返回 false → 403
        assertFalse(preHandle("badExpr"));
        verify(errorResponseWriter).write(any(HttpServletResponse.class), eq(ResultCode.FORBIDDEN), isNull());
    }

    @Test
    void testPermissionDisabled() throws Exception {
        properties.setEnabled(false);

        assertTrue(preHandle("adminOnly"));
    }

    @Test
    void testCombinedAndPass() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("admin"));
        when(permissionProvider.getPermissions(any())).thenReturn(List.of(new Permission("order", "w")));

        assertTrue(preHandle("adminAndWriteOrder"));
    }

    @Test
    void testCombinedAndForbidden() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("admin"));
        when(permissionProvider.getPermissions(any())).thenReturn(Collections.emptyList());

        assertFalse(preHandle("adminAndWriteOrder"));
    }

    private boolean preHandle(String methodName) throws Exception {
        HandlerMethod handler = new HandlerMethod(new TestController(), TestController.class.getMethod(methodName));
        return interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);
    }

    private boolean classLevelPreHandle() throws Exception {
        HandlerMethod handler = new HandlerMethod(new AdminController(), AdminController.class.getMethod("m"));
        return interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    static class TestController {

        @RequireAuth("role('admin')")
        public String adminOnly() {
            return "admin";
        }

        @RequireAuth("role('admin') or role('operator')")
        public String anyAdminOrOperator() {
            return "any";
        }

        @RequireAuth("perm('user')")
        public String readUser() {
            return "user";
        }

        @RequireAuth("perm('user') or perm('order')")
        public String readUserOrOrder() {
            return "any";
        }

        @RequireAuth("role('admin') and perm('order', 'w')")
        public String adminAndWriteOrder() {
            return "admin-order-w";
        }

        @RequireAuth("role('admin'")
        public String badExpr() {
            return "bad";
        }

        public String noAnnotation() {
            return "none";
        }

        @Anonymous
        public String publicEndpoint() {
            return "public";
        }
    }

    @RequireAuth("role('admin')")
    static class AdminController {

        public String m() {
            return "m";
        }
    }
}
