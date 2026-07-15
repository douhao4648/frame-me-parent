package com.frame.me.auth.rbac.filter;

import tools.jackson.databind.ObjectMapper;
import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.auth.rbac.permission.AuthPermissionHolder;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.Permission;
import com.frame.me.base.user.User;
import com.frame.me.base.web.ResultFilterErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionFilter} 单元测试.
 *
 * @author frame-me
 */
class PermissionFilterTest {

    private RbacProperties properties;
    private IAuthPermissionProvider permissionProvider;
    private PermissionFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RbacProperties();
        permissionProvider = mock(IAuthPermissionProvider.class);
        filter = new PermissionFilter(properties, permissionProvider,
                new ResultFilterErrorResponseWriter(new ObjectMapper()));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        AuthPermissionHolder.clear();
    }

    @Test
    void testNoRulePass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
    }

    @Test
    void testRolePass() throws Exception {
        properties.getRules().put("/api/admin/**", "role('admin')");
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("admin"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
    }

    @Test
    void testRoleForbidden() throws Exception {
        properties.getRules().put("/api/admin/**", "role('admin')");
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("operator"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("403"));
        assertNull(chain.getRequest());
    }

    @Test
    void testPermPass() throws Exception {
        properties.getRules().put("/api/order/**", "perm('order')");
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getPermissions(any())).thenReturn(List.of(new Permission("order", "r")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/order/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
    }

    @Test
    void testCombinedAndPass() throws Exception {
        properties.getRules().put("/api/admin/**", "role('admin') and perm('order', 'w')");
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("admin"));
        when(permissionProvider.getPermissions(any())).thenReturn(List.of(new Permission("order", "w")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
    }

    @Test
    void testUnauthenticatedReturns401() throws Exception {
        properties.getRules().put("/api/admin/**", "role('admin')");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // 命中权限规则但未登录：统一 Result（HTTP 200，body code 401），不再放行
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("401"));
        assertNull(chain.getRequest());
    }

    @Test
    void testPermissionDisabled() throws Exception {
        properties.setEnabled(false);
        properties.getRules().put("/api/admin/**", "role('admin')");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
