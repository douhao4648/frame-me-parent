package com.frame.me.redis.config;

import com.frame.me.redis.util.RedisUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedisAutoConfiguration} 额外实例连接工厂生命周期测试.
 *
 * <p>验证额外实例的 {@link LettuceConnectionFactory} 在容器销毁时被关闭，避免 TCP/NIO 资源泄漏.</p>
 *
 * @author frame-me
 */
class RedisAutoConfigurationTest {

    @AfterEach
    void clearRedisUtils() {
        // RedisUtils 是静态持有，跨用例清理避免串扰.
        RedisUtils.init("default", Map.of(), Map.of());
    }

    /**
     * 额外实例的 LettuceConnectionFactory 收集到本类字段、destroy 时被销毁并清空.
     *
     * <p>默认实例的 template（Boot 提供）此处用 stub 替身，避免拉起真实 Boot 上下文与网络连接；
     * 额外实例的工厂即使指向不可达 host 也能创建（Lettuce 是惰性连接），destroy 走的是关闭 native client 的本地路径.</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    void extraConnectionFactoryCollectedAndDestroyed() throws Exception {
        RedisProperties redisProperties = new RedisProperties();
        RedisProperties.ClientConfig extra = new RedisProperties.ClientConfig();
        extra.setHost("127.0.0.1");
        extra.setPort(6391);
        redisProperties.getClients().put("extra", extra);

        RedisAutoConfiguration configuration = new RedisAutoConfiguration(
                new StringRedisTemplate(), new RedisTemplate<>(), redisProperties);
        configuration.init();

        // 额外实例已注册到 RedisUtils
        assertThat(RedisUtils.getClient("extra")).isNotNull();

        // 额外实例的工厂已被收集到本类字段
        Field field = RedisAutoConfiguration.class.getDeclaredField("extraConnectionFactories");
        field.setAccessible(true);
        List<LettuceConnectionFactory> factories =
                (List<LettuceConnectionFactory>) field.get(configuration);
        assertThat(factories).hasSize(1);

        // destroy 走通、清空字段
        configuration.destroy();
        assertThat(((List<LettuceConnectionFactory>) field.get(configuration)).isEmpty()).isTrue();
    }
}
