package com.frame.me.cloud.shutdown;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GracefulShutdownEndpoint} 测试.
 *
 * <p>token 走 URL 路径段（{@code @Selector}），与 Web 栈无关，测试直接调方法传参.</p>
 *
 * @author frame-me
 */
class GracefulShutdownEndpointTest {

    @Test
    void offline_invokesExecutorAndReturnsShuttingDown() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownEndpoint endpoint = newEndpoint(flag, new GracefulShutdownProperties());

        Map<String, Object> result = endpoint.offline("any");

        assertThat(result).containsEntry("status", "shutting-down");
        assertThat(flag.isReady()).isFalse();
    }

    /**
     * 配置了 endpoint-token：路径段缺失语义（任意非匹配值）/不匹配拒绝且不触发下线；匹配则正常下线.
     */
    @Test
    void offline_rejectsWrongTokenAndAcceptsCorrectToken() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownProperties props = new GracefulShutdownProperties();
        props.setEndpointToken("s3cret");
        GracefulShutdownEndpoint endpoint = newEndpoint(flag, props);

        // 错 token → forbidden，flag 不变
        Map<String, Object> denied = endpoint.offline("wrong");
        assertThat(denied).containsEntry("status", "forbidden");
        assertThat(flag.isReady()).isTrue();

        // 正确 token → 正常下线
        Map<String, Object> ok = endpoint.offline("s3cret");
        assertThat(ok).containsEntry("status", "shutting-down");
        assertThat(flag.isReady()).isFalse();
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
