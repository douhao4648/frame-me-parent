package com.frame.me.redis.config;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link RedissonAutoConfiguration} 条件与校验测试.
 *
 * @author frame-me
 */
class RedissonAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedissonAutoConfiguration.class));

    /**
     * 业务自定义 RedissonClient 时，meRedissonClient 不再创建（避免覆盖自定义配置）.
     */
    @Test
    void backsOffWhenCustomRedissonClientPresent() {
        runner.withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> assertThat(context).doesNotHaveBean("meRedissonClient"));
    }

    /**
     * 哨兵模式配置了 master 但未配 nodes：buildConfig fail-fast 抛 IllegalArgumentException 并给出可读提示，
     * 而非 assert（生产 -da 下被剥离）导致的不可读 NPE.
     *
     * <p>直接反射调用 private buildConfig，绕过 Spring 装配与真实 Redisson 连接.</p>
     */
    @Test
    void sentinelWithoutNodesFailsFastWithReadableMessage() throws Exception {
        DataRedisProperties dataRedisProperties = new DataRedisProperties();
        // Boot 4 的 DataRedisProperties 未配置时 getSentinel() 为 null，需手动构造.
        DataRedisProperties.Sentinel sentinel = new DataRedisProperties.Sentinel();
        sentinel.setMaster("mymaster");
        dataRedisProperties.setSentinel(sentinel);

        RedissonAutoConfiguration configuration = new RedissonAutoConfiguration();
        Method buildConfig = RedissonAutoConfiguration.class.getDeclaredMethod(
                "buildConfig", DataRedisProperties.class);
        buildConfig.setAccessible(true);

        assertThatThrownBy(() -> buildConfig.invoke(configuration, dataRedisProperties))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("哨兵模式必须提供至少一个节点");
    }
}
