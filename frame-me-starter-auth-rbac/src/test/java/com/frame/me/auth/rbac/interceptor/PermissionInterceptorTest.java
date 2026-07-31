package com.frame.me.auth.rbac.interceptor;

import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.auth.rbac.annotation.RequireAuth;
import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.auth.rbac.permission.AuthPermissionHolder;
import com.frame.me.auth.rbac.permission.DataPermission;
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
import org.springframework.web.servlet.HandlerMapping;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void testTrueLiteralActsAsLoadSwitch() throws Exception {
        // "true" 字面量:登录即放行(无角色/权限也可),同时触发权限加载供 Helper 使用
        AuthContext.setUser(createUser(1L));

        assertTrue(preHandle("authenticatedOnly"));
        assertTrue(AuthPermissionHolder.isLoaded(), "恒真表达式应触发权限加载");
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

    @Test
    void testQueryParamDataPermPass() throws Exception {
        // 查询参数 ?id=5 注入 SpEL 变量,dataCheck 命中 CUSTOM ids 放行
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getDataPermissions(any())).thenReturn(
                List.of(new DataPermission("order", "*", "CUSTOM", Set.of(5L, 7L))));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(Map.of("id", new String[]{"5"}));

        assertTrue(preHandle("orderDetail", request));
    }

    @Test
    void testQueryParamDataPermForbidden() throws Exception {
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getDataPermissions(any())).thenReturn(
                List.of(new DataPermission("order", "*", "CUSTOM", Set.of(5L, 7L))));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(Map.of("id", new String[]{"6"}));

        assertFalse(preHandle("orderDetail", request));
        verify(errorResponseWriter).write(any(HttpServletResponse.class), eq(ResultCode.FORBIDDEN), isNull());
    }

    @Test
    void testUriVariableWinsOverQueryParam() throws Exception {
        // 同名时路径变量优先:校验值必须与 @PathVariable 绑定值一致(防 /api/order/5?id=6 越权窗口)
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getDataPermissions(any())).thenReturn(
                List.of(new DataPermission("order", "*", "CUSTOM", Set.of(5L, 7L))));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(Map.of("id", new String[]{"6"}));
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(Map.of("id", "5"));

        assertTrue(preHandle("orderDetail", request));
    }

    /**
     * OPTIONS 预检请求带 Origin 头时，即使命中 {@code @RequireAuth} 方法且未登录，也直接放行，不返回 401：
     * 预检不应被权限校验拦截，否则浏览器跨域预检失败。
     * 与 AuthFilter 的『OPTIONS + Origin』豁免口径一致。
     */
    @Test
    void testOptionsPreflightWithOriginPassesWithoutAuth() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("OPTIONS");
        when(request.getHeader("Origin")).thenReturn("https://app.example.com");

        assertTrue(preHandle("adminOnly", request));
        verifyNoInteractions(errorResponseWriter);
    }

    /**
     * 无 Origin 的 OPTIONS 非真 CORS 预检，不豁免，走正常权限链（防绕过）.
     * 命中 {@code @RequireAuth} 且无 AuthContext 时应被拦截（preHandle 返回 false）。
     */
    @Test
    void testOptionsWithoutOriginNotSkipped() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("OPTIONS");
        when(request.getHeader("Origin")).thenReturn(null);

        assertFalse(preHandle("adminOnly", request));
    }

    private boolean preHandle(String methodName) throws Exception {
        return preHandle(methodName, mock(HttpServletRequest.class));
    }

    private boolean preHandle(String methodName, HttpServletRequest request) throws Exception {
        HandlerMethod handler = new HandlerMethod(new TestController(), TestController.class.getMethod(methodName));
        return interceptor.preHandle(request, mock(HttpServletResponse.class), handler);
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

        @RequireAuth("dataCheck('order', #id)")
        public String orderDetail() {
            return "detail";
        }

        @RequireAuth("true")
        public String authenticatedOnly() {
            return "ok";
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
