package com.frame.me.cloud.shutdown;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GracefulShutdownEndpoint} 测试.
 *
 * @author frame-me
 */
class GracefulShutdownEndpointTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void offline_invokesExecutorAndReturnsShuttingDown() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownEndpoint endpoint = newEndpoint(flag, new GracefulShutdownProperties());

        Map<String, Object> result = endpoint.offline();

        assertThat(result).containsEntry("status", "shutting-down");
        assertThat(flag.isReady()).isFalse();
    }

    /**
     * 配置了 endpoint-token：请求头缺失/不匹配 403 且不触发下线；匹配则正常下线.
     */
    @Test
    void offline_rejectsWrongTokenAndAcceptsCorrectToken() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownProperties props = new GracefulShutdownProperties();
        props.setEndpointToken("s3cret");
        GracefulShutdownEndpoint endpoint = newEndpoint(flag, props);

        // 无头 → 403，flag 不变
        MockHttpServletResponse deniedRes = bindRequest(null);
        Map<String, Object> denied = endpoint.offline();
        assertThat(denied).containsEntry("status", "forbidden");
        assertThat(deniedRes.getStatus()).isEqualTo(403);
        assertThat(flag.isReady()).isTrue();

        // 错 token → 403，flag 不变
        bindRequest("wrong");
        assertThat(endpoint.offline()).containsEntry("status", "forbidden");
        assertThat(flag.isReady()).isTrue();

        // 正确 token → 正常下线
        bindRequest("s3cret");
        Map<String, Object> ok = endpoint.offline();
        assertThat(ok).containsEntry("status", "shutting-down");
        assertThat(flag.isReady()).isFalse();
    }

    private MockHttpServletResponse bindRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader(GracefulShutdownEndpoint.TOKEN_HEADER, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        return response;
    }

    private static GracefulShutdownEndpoint newEndpoint(ShutdownReadyFlag flag, GracefulShutdownProperties props) {
        props.setDeregisterWait(Duration.ZERO);
        ObjectProvider<ServiceRegistry<Registration>> emptyRegistry = mockEmptyProvider();
        ObjectProvider<Registration> emptyRegistration = mockEmptyProvider();
        GracefulShutdownExecutor executor = new GracefulShutdownExecutor(
                flag, props, emptyRegistry, emptyRegistration);
        return new GracefulShutdownEndpoint(executor, props);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockEmptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
