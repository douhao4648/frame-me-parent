package com.frame.me.redis.config;

import com.frame.me.base.event.EventBridgeAutoConfiguration;
import com.frame.me.base.event.EventBridgeProperties;
import com.frame.me.redis.event.RedisEventTransport;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 事件传输自动配置.
 *
 * <p>仅在 classpath 存在 {@link RedissonClient}、容器中存在 {@link RedissonClient} 与
 * {@link EventBridgeProperties} bean 且 {@code me.event-bridge.enabled=true} 时装配
 * {@link RedisEventTransport}。</p>
 *
 * <p>{@link ConditionalOnBean} 依赖 {@link AutoConfigureAfter} 保证处理顺序：
 * {@link RedissonClient} bean 由 {@link RedissonAutoConfiguration} 创建（同时完成
 * {@code RedissonTopic} 静态初始化），{@link EventBridgeProperties} 由
 * {@link EventBridgeAutoConfiguration} 注册；不依赖类名字典序的偶然正确。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({EventBridgeAutoConfiguration.class, RedissonAutoConfiguration.class})
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnBean({RedissonClient.class, EventBridgeProperties.class})
@ConditionalOnProperty(prefix = "me.event-bridge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisEventTransportAutoConfiguration {

    @Bean
    public RedisEventTransport redisEventTransport(EventBridgeProperties properties) {
        log.info("Registering RedisEventTransport with topic prefix: {}", properties.getTopicPrefix());
        return new RedisEventTransport(properties.getTopicPrefix());
    }
}
