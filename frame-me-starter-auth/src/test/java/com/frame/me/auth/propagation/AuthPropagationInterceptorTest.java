package com.frame.me.auth.propagation;

import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link AuthPropagationInterceptor} 单元测试.
 *
 * @author frame-me
 */
class AuthPropagationInterceptorTest {

    private AuthProperties properties;
    private AuthPropagationInterceptor interceptor;
    private MockClientHttpRequest outboundRequest;
    private ClientHttpRequestExecution execution;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.getPropagate().setAllowedHosts(List.of("downstream"));
        interceptor = new AuthPropagationInterceptor(properties);
        outboundRequest = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://downstream/api/demo"));
        execution = (request, body) -> new MockClientHttpResponse(new byte[0], 200);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        AuthContext.clear();
        AuthPropagationHolder.clear();
    }

    @Test
    void testPropagateAuthorizationHeader() throws IOException {
        bindRequestWithHeader("Authorization", "Bearer token123");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("Bearer token123", outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testPropagateUserInfoHeaders() throws IOException {
        bindRequestWithHeader("X-User-Id", "1");
        bindRequestWithHeader("X-User-Account", "admin");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("1", outboundRequest.getHeaders().getFirst("X-User-Id"));
        assertEquals("admin", outboundRequest.getHeaders().getFirst("X-User-Account"));
    }

    @Test
    void testSupplementUserInfoFromAuthContext() throws IOException {
        bindRequestWithHeader("Authorization", "Bearer token123");
        AuthContext.setUser(createUser(1L, "admin"));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("Bearer token123", outboundRequest.getHeaders().getFirst("Authorization"));
        assertEquals("1", outboundRequest.getHeaders().getFirst("X-User-Id"));
        assertEquals("admin", outboundRequest.getHeaders().getFirst("X-User-Account"));
    }

    @Test
    void testContextUserInfoDoesNotOverrideExistingHeader() throws IOException {
        bindRequestWithHeader("Authorization", "Bearer token123");
        outboundRequest.getHeaders().add("X-User-Id", "999");
        AuthContext.setUser(createUser(1L, "admin"));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("999", outboundRequest.getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void testSkipWhenNoCurrentRequestAndNoContext() throws IOException {
        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertNull(outboundRequest.getHeaders().getFirst("Authorization"));
        assertNull(outboundRequest.getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void testPropagateHeaderFromAuthPropagationHolder() throws IOException {
        AuthPropagationHolder.setHeaders(Map.of("Authorization", "Bearer holder-token"));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("Bearer holder-token", outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testRequestHeaderTakesPrecedenceOverHolder() throws IOException {
        bindRequestWithHeader("Authorization", "Bearer request-token");
        AuthPropagationHolder.setHeaders(Map.of("Authorization", "Bearer holder-token"));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("Bearer request-token", outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testDoNotOverrideExistingHeader() throws IOException {
        bindRequestWithHeader("Authorization", "Bearer token123");
        outboundRequest.getHeaders().add("Authorization", "Bearer existing");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("Bearer existing", outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testDisabled() throws IOException {
        properties.getPropagate().setEnabled(false);
        bindRequestWithHeader("Authorization", "Bearer token123");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertNull(outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testCustomHeaders() throws IOException {
        properties.getPropagate().setHeaders(List.of("X-Auth-Token"));
        bindRequestWithHeader("X-Auth-Token", "secret-token");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("secret-token", outboundRequest.getHeaders().getFirst("X-Auth-Token"));
        assertNull(outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testUserInfoDisabled() throws IOException {
        properties.getPropagate().getUserInfo().setEnabled(false);
        bindRequestWithHeader("Authorization", "Bearer token123");
        AuthContext.setUser(createUser(1L, "admin"));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("Bearer token123", outboundRequest.getHeaders().getFirst("Authorization"));
        assertNull(outboundRequest.getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void testAllowedHostsExactMatch() throws IOException {
        properties.getPropagate().setAllowedHosts(List.of("downstream"));
        bindRequestWithHeader("Authorization", "Bearer token123");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("Bearer token123", outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testAllowedHostsWildcardSuffix() throws IOException {
        properties.getPropagate().setAllowedHosts(List.of("*.internal.example.com"));
        outboundRequest = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://order.internal.example.com/api/demo"));
        bindRequestWithHeader("Authorization", "Bearer token123");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("Bearer token123", outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testWildcardSuffixDoesNotMatchBareDomain() throws IOException {
        properties.getPropagate().setAllowedHosts(List.of("*.internal.example.com"));
        outboundRequest = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://internal.example.com/api/demo"));
        bindRequestWithHeader("Authorization", "Bearer token123");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertNull(outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testDisallowedHostSkipped() throws IOException {
        properties.getPropagate().setAllowedHosts(List.of("trusted.internal"));
        outboundRequest = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://external.evil-corp.com/api/demo"));
        bindRequestWithHeader("Authorization", "Bearer token123");
        AuthContext.setUser(createUser(1L, "admin"));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertNull(outboundRequest.getHeaders().getFirst("Authorization"));
        assertNull(outboundRequest.getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void testExternalHostNotPropagatedByDefault() throws IOException {
        // 未配置白名单时，多标签外部域名默认不传播（fail-closed）
        outboundRequest = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://api.third-party.com/webhook"));
        bindRequestWithHeader("Authorization", "Bearer token123");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertNull(outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testProbeAllowsFullyQualifiedServiceName() throws IOException {
        // 注册中心探针可解析的多标签服务名（如 K8s 全限定名）放行
        interceptor = new AuthPropagationInterceptor(properties,
                host -> host.equals("order.default.svc.cluster.local"));
        outboundRequest = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://order.default.svc.cluster.local/api/demo"));
        bindRequestWithHeader("Authorization", "Bearer token123");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("Bearer token123", outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testSingleLabelHostBlockedWithoutProbe() throws IOException {
        // 无探针注册时，单标签主机名（非白名单）不再无条件放行，防止 SSRF 泄漏认证头
        properties.getPropagate().setAllowedHosts(null);
        outboundRequest = new MockClientHttpRequest(HttpMethod.GET,
                URI.create("http://order-service/api/demo"));
        bindRequestWithHeader("Authorization", "Bearer token123");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertNull(outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testAllowedHostsStarMatchesAll() throws IOException {
        properties.getPropagate().setAllowedHosts(List.of("*"));
        bindRequestWithHeader("Authorization", "Bearer token123");

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("Bearer token123", outboundRequest.getHeaders().getFirst("Authorization"));
    }

    @Test
    void testCustomUserInfoHeaders() throws IOException {
        properties.getPropagate().getUserInfo().setUserIdHeader("X-Uid");
        properties.getPropagate().getUserInfo().setUserAccountHeader("X-Account");
        AuthContext.setUser(createUser(1L, "admin"));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertEquals("1", outboundRequest.getHeaders().getFirst("X-Uid"));
        assertEquals("admin", outboundRequest.getHeaders().getFirst("X-Account"));
    }

    private void bindRequestWithHeader(String name, String value) {
        MockHttpServletRequest request;
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            request = (MockHttpServletRequest) attributes.getRequest();
        } else {
            request = new MockHttpServletRequest();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        }
        request.addHeader(name, value);
    }

    private User createUser(Long id, String account) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        return user;
    }
}
