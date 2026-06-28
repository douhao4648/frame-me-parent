package com.frame.me.tester.event;

import com.frame.me.base.event.EventBridgeListener;
import com.frame.me.base.event.EventBridgePublisher;
import com.frame.me.event.EventBridgeMessage;
import com.frame.me.redis.util.RedissonTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 用户创建事件端到端流程测试.
 *
 * <p>使用 Testcontainers 启动 Redis，验证事件桥接的发布-订阅-本地消费完整链路。</p>
 *
 * @author frame-me
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class UserCreatedEventFlowTest {

    private static final boolean DOCKER_AVAILABLE;

    static {
        boolean dockerAvailable;
        try {
            DockerClientFactory.instance().client();
            dockerAvailable = true;
        } catch (Exception e) {
            dockerAvailable = false;
        }
        DOCKER_AVAILABLE = dockerAvailable;
    }

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("me.event-bridge.service-name", () -> "tester-service");
    }

    @Autowired
    private EventBridgePublisher publisher;

    @Autowired
    private UserCreatedEventHandler handler;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private EventBridgeListener eventBridgeListener;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Docker 不可用，跳过 Redis 集成测试");
        handler.clear();
    }

    @AfterEach
    void tearDown() {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        handler.clear();
    }

    @Test
    void shouldPublishAndConsumeUserCreatedEventLocally() {
        UserCreatedPayload payload = new UserCreatedPayload(1L, "alice");
        UserCreatedEvent event = new UserCreatedEvent(this, payload);

        publisher.publish(event);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(handler.getReceived())
                        .hasSize(1)
                        .first()
                        .extracting(UserCreatedPayload::getUserId, UserCreatedPayload::getUsername)
                        .containsExactly(1L, "alice"));
    }

    @Test
    void shouldConsumeEventFromRedisChannel() {
        // 模拟另一个服务通过 Redis 广播的事件消息
        UserCreatedPayload payload = new UserCreatedPayload(2L, "bob");
        String json = com.alibaba.fastjson2.JSON.toJSONString(payload);
        EventBridgeMessage message = EventBridgeMessage.of("user:created", json, "other-service");

        RedissonTopic.topicPublish("me:event:user:created", message);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(handler.getReceived())
                        .hasSize(1)
                        .first()
                        .extracting(UserCreatedPayload::getUserId, UserCreatedPayload::getUsername)
                        .containsExactly(2L, "bob"));
    }
}
