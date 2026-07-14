package com.frame.me.auth.rbac.interceptor;

import com.frame.me.auth.rbac.annotation.RequireAuth;
import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.auth.rbac.permission.AuthPermissionHolder;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.Permission;
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
import static org.mockito.Mockito.mock;
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

        HandlerMethod handler = handlerOf("adminOnly");
        boolean result = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);

        assertTrue(result);
    }

    @Test
    void testRoleForbidden() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("operator"));

        HandlerMethod handler = handlerOf("adminOnly");
        boolean result = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);

        assertFalse(result);
    }

    @Test
    void testRoleOrPass() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("operator"));

        HandlerMethod handler = handlerOf("anyAdminOrOperator");
        boolean result = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);

        assertTrue(result);
    }

    @Test
    void testPermPass() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getPermissions(any())).thenReturn(List.of(new Permission("user", "r")));

        HandlerMethod handler = handlerOf("readUser");
        boolean result = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);

        assertTrue(result);
    }

    @Test
    void testPermForbidden() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getPermissions(any())).thenReturn(Collections.emptyList());

        HandlerMethod handler = handlerOf("readUser");
        boolean result = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);

        assertFalse(result);
    }

    @Test
    void testPermOrPass() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getPermissions(any())).thenReturn(List.of(new Permission("order", "r")));

        HandlerMethod handler = handlerOf("readUserOrOrder");
        boolean result = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);

        assertTrue(result);
    }

    @Test
    void testAnonymousUserForbidden() throws Exception {
        HandlerMethod handler = handlerOf("adminOnly");
        boolean result = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);

        assertFalse(result);
    }

    @Test
    void testPermissionDisabled() throws Exception {
        properties.setEnabled(false);
        HandlerMethod handler = handlerOf("adminOnly");
        boolean result = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);

        assertTrue(result);
    }

    @Test
    void testCombinedAndPass() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("admin"));
        when(permissionProvider.getPermissions(any())).thenReturn(List.of(new Permission("order", "w")));

        HandlerMethod handler = handlerOf("adminAndWriteOrder");
        boolean result = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);

        assertTrue(result);
    }

    @Test
    void testCombinedAndForbidden() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("admin"));
        when(permissionProvider.getPermissions(any())).thenReturn(Collections.emptyList());

        HandlerMethod handler = handlerOf("adminAndWriteOrder");
        boolean result = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handler);

        assertFalse(result);
    }

    private HandlerMethod handlerOf(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new TestController(), TestController.class.getMethod(methodName));
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
    }
}
