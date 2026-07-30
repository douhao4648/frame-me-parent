package com.frame.me.auth.filter;

import tools.jackson.databind.ObjectMapper;
import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.base.user.User;
import com.frame.me.base.web.IFilterErrorResponseWriter;
import com.frame.me.base.web.ResultFilterErrorResponseWriter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AuthFilter} 单元测试.
 *
 * @author frame-me
 */
class AuthFilterTest {

    private IAuthUserResolver userResolver;
    private RequestMappingHandlerMapping handlerMapping;
    private AuthProperties properties;
    private IFilterErrorResponseWriter errorResponseWriter;
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        userResolver = mock(IAuthUserResolver.class);
        handlerMapping = mock(RequestMappingHandlerMapping.class);
        properties = new AuthProperties();
        errorResponseWriter = new ResultFilterErrorResponseWriter(new ObjectMapper());
        filter = new AuthFilter(userResolver, handlerMapping, properties, errorResponseWriter);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void testWhitelistPassWithoutAuth() throws Exception {
        properties.setWhitelist(List.of("/api/public/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/info");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
    }

    @Test
    void testWhitelistPassWithContextPath() throws Exception {
        // 配置 context-path 后白名单按应用内路径匹配，仍应命中放行
        properties.setWhitelist(List.of("/api/public/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/public/info");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
    }

    @Test
    void testWhitelistWithContextPathPrefixAlsoMatches() throws Exception {
        // 白名单误带 context-path 前缀时平滑兼容，同样命中放行
        properties.setWhitelist(List.of("/app/api/public/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/public/info");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
    }

    @Test
    void testAnonymousAnnotationPass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anon");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        HandlerMethod handlerMethod = new HandlerMethod(new AnonController(),
                AnonController.class.getMethod("anon"));
        when(handlerMapping.getHandler(any(HttpServletRequest.class)))
                .thenReturn(new HandlerExecutionChain(handlerMethod));

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
    }

    @Test
    void testUnauthorizedRequestBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(handlerMapping.getHandler(any(HttpServletRequest.class))).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNull(chain.getRequest());
        assertTrue(response.getContentAsString().contains("401"));
    }

    @Test
    void testAuthorizedRequestPass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(handlerMapping.getHandler(any(HttpServletRequest.class))).thenReturn(null);

        User user = new User();
        user.setId(1L);
        user.setAccount("admin");
        when(userResolver.resolve(any(HttpServletRequest.class))).thenReturn(user);

        java.util.concurrent.atomic.AtomicBoolean invoked = new java.util.concurrent.atomic.AtomicBoolean(false);
        FilterChain chain = (req, res) -> {
            invoked.set(true);
            assertEquals(user, AuthContext.getUser());
        };

        filter.doFilter(request, response, chain);

        assertTrue(invoked.get());
    }

    @Test
    void testContextClearedAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(handlerMapping.getHandler(any(HttpServletRequest.class))).thenReturn(null);

        User user = new User();
        user.setId(1L);
        when(userResolver.resolve(any(HttpServletRequest.class))).thenReturn(user);

        filter.doFilter(request, response, chain);

        assertNull(AuthContext.getUser());
    }

    @Test
    void testEnforceLoginOffPassWithoutAuth() throws Exception {
        properties.setEnforceLogin(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(handlerMapping.getHandler(any(HttpServletRequest.class))).thenReturn(null);

        java.util.concurrent.atomic.AtomicBoolean invoked = new java.util.concurrent.atomic.AtomicBoolean(false);
        FilterChain chain = (req, res) -> invoked.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void testEnforceLoginOffResolvesUserWhenAvailable() throws Exception {
        properties.setEnforceLogin(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(handlerMapping.getHandler(any(HttpServletRequest.class))).thenReturn(null);

        User user = new User();
        user.setId(1L);
        user.setAccount("admin");
        when(userResolver.resolve(any(HttpServletRequest.class))).thenReturn(user);

        java.util.concurrent.atomic.AtomicBoolean invoked = new java.util.concurrent.atomic.AtomicBoolean(false);
        FilterChain chain = (req, res) -> {
            invoked.set(true);
            assertEquals(user, AuthContext.getUser());
        };

        filter.doFilter(request, response, chain);

        assertTrue(invoked.get());
    }

    /**
     * ERROR dispatch（容器 /error 转发）直接放行：
     * 不做白名单/登录校验（真实状态码不被 401 掩盖），也不触碰用户解析.
     */
    @Test
    void testErrorDispatchPassesThroughWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
        verifyNoInteractions(handlerMapping, userResolver);
    }

    /**
     * ERROR dispatch 不清理 AuthContext —— ThreadLocal 由 REQUEST dispatch 的 finally 负责.
     */
    @Test
    void testErrorDispatchDoesNotClearContext() throws Exception {
        User user = new User();
        user.setId(1L);
        AuthContext.setUser(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(user, AuthContext.getUser());
    }

    /**
     * OPTIONS 预检请求直接放行，不触发认证逻辑，不解析用户：
     * 即使未登录、非白名单，预检也应穿透到后续 CorsFilter/控制器。
     */
    @Test
    void testOptionsPreflightPassesWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(request, chain.getRequest());
        verifyNoInteractions(userResolver);
    }

    @Anonymous
    static class AnonController {
        @Anonymous
        public String anon() {
            return "anon";
        }
    }
}
