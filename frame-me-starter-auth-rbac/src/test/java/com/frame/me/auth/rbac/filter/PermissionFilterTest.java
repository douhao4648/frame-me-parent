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

    /**
     * 重叠规则：更宽泛的 /api/** 在前、更具体的 /api/admin/** 在后时，
     * 最长模式优先，/api/admin/users 命中 admin 规则而非被 user 规则降级（fail-open）.
     */
    @Test
    void overlappingRules_longerPatternWins() throws Exception {
        // 顺序故意反着写：宽泛在前，具体在后
        properties.getRules().put("/api/**", "role('user')");
        properties.getRules().put("/api/admin/**", "role('admin')");
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("user"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // user 角色应被 admin 规则拒绝，而非命中 /api/** 放行
        assertTrue(response.getContentAsString().contains("403"));
        assertNull(chain.getRequest());
    }

    /**
     * 长度相近的相似模式：精确模式 /api/users（10 字符）必须胜过更长的通配模式
     * /api/user-*（11 字符）。长度启发式会误选更长的通配规则导致 fail-open，
     * AntPathMatcher 官方比较器按具体度排序（精确 > 单段通配 > **）.
     */
    @Test
    void similarLengthPatterns_exactWinsOverLongerWildcard() throws Exception {
        properties.getRules().put("/api/users", "role('admin')");
        properties.getRules().put("/api/user-*", "role('user')");
        AuthContext.setUser(createUser(1L));
        when(permissionProvider.getRoles(any())).thenReturn(List.of("user"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // 精确模式命中 admin 规则：user 角色应被拒绝，而非命中更长的通配规则放行
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
    void testRuleMatchesWithContextPath() throws Exception {
        // 配置 context-path 后规则按应用内路径匹配，仍应命中而非静默放行
        properties.getRules().put("/api/admin/**", "role('admin')");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/admin/users");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // 命中规则且未登录 → 401；若规则失配则会放行（fail-open 回归）
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("401"));
        assertNull(chain.getRequest());
    }

    @Test
    void testRuleWithContextPathPrefixAlsoMatches() throws Exception {
        // 规则误带 context-path 前缀时平滑兼容，同样命中而非静默放行
        properties.getRules().put("/app/api/admin/**", "role('admin')");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/admin/users");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

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

    /**
     * OPTIONS 预检请求带 Origin 头时，即使命中权限规则且未登录，也直接放行，不返回 401：
     * 预检不应被权限校验拦截，否则浏览器跨域预检失败。
     * 与 AuthFilter 的『OPTIONS + Origin』豁免口径一致。
     */
    @Test
    void testOptionsPreflightWithOriginPassesEvenWithRuleAndNoAuth() throws Exception {
        properties.getRules().put("/api/admin/**", "role('admin')");

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/admin/users");
        request.addHeader("Origin", "https://app.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
        assertTrue(response.getContentAsString().isEmpty() || !response.getContentAsString().contains("401"));
    }

    /**
     * 无 Origin 的 OPTIONS 非真 CORS 预检，不豁免，走正常权限链（防绕过）.
     * 命中规则且无 AuthContext 时应被拦截，而非像真预检那样放行。
     */
    @Test
    void testOptionsWithoutOriginNotSkipped() throws Exception {
        properties.getRules().put("/api/admin/**", "role('admin')");

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // 未豁免：命中规则且无用户上下文，应被权限校验拦截（chain 未被调用）
        assertNull(chain.getRequest());
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
