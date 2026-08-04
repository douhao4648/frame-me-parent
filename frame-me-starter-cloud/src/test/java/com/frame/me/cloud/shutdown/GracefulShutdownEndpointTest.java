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
 * @author frame-me
 */
class GracefulShutdownEndpointTest {

    @Test
    @SuppressWarnings("unchecked")
    void offline_invokesExecutorAndReturnsShuttingDown() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownProperties props = new GracefulShutdownProperties();
        props.setDeregisterWait(Duration.ZERO);
        ObjectProvider<ServiceRegistry<Registration>> emptyRegistry = mockEmptyProvider();
        ObjectProvider<Registration> emptyRegistration = mockEmptyProvider();

        GracefulShutdownExecutor executor = new GracefulShutdownExecutor(
                flag, props, emptyRegistry, emptyRegistration);
        GracefulShutdownEndpoint endpoint = new GracefulShutdownEndpoint(executor);

        Map<String, Object> result = endpoint.offline();

        assertThat(result).containsEntry("status", "shutting-down");
        assertThat(flag.isReady()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockEmptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
