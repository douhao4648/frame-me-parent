package com.frame.me.cloud.shutdown;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link GracefulShutdownExecutor} 下线编排测试.
 *
 * @author frame-me
 */
class GracefulShutdownExecutorTest {

    @Test
    void shutdown_marksFlagFalseAndSkipsDeregisterWhenNoRegistry() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownProperties props = new GracefulShutdownProperties();
        props.setDeregisterWait(Duration.ZERO); // 测试不等待

        ObjectProvider<ServiceRegistry<Registration>> emptyRegistry = mockEmptyProvider();
        ObjectProvider<Registration> emptyRegistration = mockEmptyProvider();

        GracefulShutdownExecutor executor = new GracefulShutdownExecutor(
                flag, props, emptyRegistry, emptyRegistration);

        executor.shutdown();

        assertThat(flag.isReady()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdown_deregistersWhenRegistryPresent() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownProperties props = new GracefulShutdownProperties();
        props.setDeregisterWait(Duration.ZERO);

        ServiceRegistry<Registration> registry = mock(ServiceRegistry.class);
        Registration registration = mock(Registration.class);
        ObjectProvider<ServiceRegistry<Registration>> registryProvider = mockProvider(registry);
        ObjectProvider<Registration> registrationProvider = mockProvider(registration);

        GracefulShutdownExecutor executor = new GracefulShutdownExecutor(
                flag, props, registryProvider, registrationProvider);

        executor.shutdown();

        assertThat(flag.isReady()).isFalse();
        verify(registry).deregister(registration);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdown_deregisterFailureDoesNotBlockShutdown() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownProperties props = new GracefulShutdownProperties();
        props.setDeregisterWait(Duration.ZERO);

        ServiceRegistry<Registration> registry = mock(ServiceRegistry.class);
        doThrow(new RuntimeException("nacos down")).when(registry).deregister(any());
        Registration registration = mock(Registration.class);
        ObjectProvider<ServiceRegistry<Registration>> registryProvider = mockProvider(registry);
        ObjectProvider<Registration> registrationProvider = mockProvider(registration);

        GracefulShutdownExecutor executor = new GracefulShutdownExecutor(
                flag, props, registryProvider, registrationProvider);

        // 反注册抛异常不阻断流程
        executor.shutdown();

        assertThat(flag.isReady()).isFalse();
        verify(registry).deregister(registration);
    }

    @Test
    void shutdown_idempotent_multipleCallsNoSideEffect() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownProperties props = new GracefulShutdownProperties();
        props.setDeregisterWait(Duration.ZERO);

        ObjectProvider<ServiceRegistry<Registration>> emptyRegistry = mockEmptyProvider();
        ObjectProvider<Registration> emptyRegistration = mockEmptyProvider();

        GracefulShutdownExecutor executor = new GracefulShutdownExecutor(
                flag, props, emptyRegistry, emptyRegistration);

        executor.shutdown();
        executor.shutdown(); // 幂等：重复调用无副作用

        assertThat(flag.isReady()).isFalse();
    }

    @Test
    void shutdown_skipsWaitWhenNoRegistry() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownProperties props = new GracefulShutdownProperties();
        props.setDeregisterWait(Duration.ofSeconds(60)); // 无注册中心时不得等待

        GracefulShutdownExecutor executor = new GracefulShutdownExecutor(
                flag, props, mockEmptyProvider(), mockEmptyProvider());

        long start = System.nanoTime();
        executor.shutdown();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(1_000);
    }

    @Test
    void shutdown_waitsAfterSuccessfulDeregister() {
        ShutdownReadyFlag flag = new ShutdownReadyFlag();
        GracefulShutdownProperties props = new GracefulShutdownProperties();
        props.setDeregisterWait(Duration.ofMillis(150));

        ServiceRegistry<Registration> registry = mock(ServiceRegistry.class);
        Registration registration = mock(Registration.class);

        GracefulShutdownExecutor executor = new GracefulShutdownExecutor(
                flag, props, mockProvider(registry), mockProvider(registration));

        long start = System.nanoTime();
        executor.shutdown();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        verify(registry).deregister(registration);
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(150);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockEmptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> mockProvider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
