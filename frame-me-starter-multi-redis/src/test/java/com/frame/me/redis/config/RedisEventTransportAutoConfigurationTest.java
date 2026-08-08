package com.frame.me.redis.config;

import com.frame.me.base.event.EventBridgeProperties;
import com.frame.me.redis.event.RedisEventTransport;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link RedisEventTransportAutoConfiguration} 条件装配测试.
 *
 * @author frame-me
 */
class RedisEventTransportAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisEventTransportAutoConfiguration.class))
            .withBean(EventBridgeProperties.class);

    @Test
    void shouldConfigureTransportWhenRedissonClientBeanPresent() {
        runner.withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> assertThat(context).hasSingleBean(RedisEventTransport.class));
    }

    /**
     * Redisson jar 在 classpath 但 RedissonClient bean 未创建（如 me.redis.enabled=false
     * 关闭了 RedissonAutoConfiguration）时不装配 transport，
     * 避免运行时 RedissonTopic 未初始化才炸.
     */
    @Test
    void shouldNotConfigureTransportWhenRedissonClientBeanMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RedisEventTransport.class));
    }

    @Test
    void shouldNotConfigureTransportWhenEventBridgeDisabled() {
        runner.withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .withPropertyValues("me.event-bridge.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RedisEventTransport.class));
    }

    @Test
    void shouldNotConfigureTransportWhenEventBridgePropertiesMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisEventTransportAutoConfiguration.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> assertThat(context).doesNotHaveBean(RedisEventTransport.class));
    }

    /**
     * redisson 是 multi-redis 的 optional 依赖，消费方未引入时（jar 完全不在 classpath）
     * {@link RedisEventTransportAutoConfiguration} 必须优雅跳过，不抛 NoClassDefFoundError.
     *
     * <p>用 FilteredClassLoader 屏蔽 redisson 包模拟缺席。验证 {@code @ConditionalOnClass}
     * 与 {@code @ConditionalOnBean} 在类缺失时整体退避.</p>
     */
    @Test
    void shouldNotConfigureTransportWhenRedissonJarAbsent() {
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("org.redisson"))
                .withConfiguration(AutoConfigurations.of(RedisEventTransportAutoConfiguration.class))
                .withBean(EventBridgeProperties.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RedisEventTransport.class);
                    assertThat(context.getStartupFailure()).as("redisson 缺席时不得启动失败").isNull();
                });
    }
}
