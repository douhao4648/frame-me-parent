package com.frame.me.base.event;

import com.frame.me.event.IEventType;
import com.frame.me.event.MeApplicationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link EventBridgeListener} 自过滤单元测试（实例级默认 + service 回程）.
 *
 * @author frame-me
 */
class EventBridgeListenerTest {

    private static final String SELF_NAME = "frame-me-sso";
    private static final String SELF_INSTANCE = "self-instance-1";

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private EventBridgeProperties properties;
    private EventBridgeListener listener;

    @BeforeEach
    void setUp() {
        properties = new EventBridgeProperties();
        properties.setServiceName(SELF_NAME);
        properties.setInstanceId(SELF_INSTANCE);
        listener = new EventBridgeListener(publisher, properties, Map.of());
        listener.register(new StubEventType());
    }

    /**
     * 实例级（默认）：instanceId 相同（本 JVM 回声）→ 丢弃，不触发本地二次分发.
     */
    @Test
    void sameInstanceMessageIsFilteredInInstanceMode() {
        listener.onMessage(message(SELF_NAME, SELF_INSTANCE));

        verifyNoInteractions(publisher);
    }

    /**
     * 实例级（默认）：同服务名但 instanceId 不同（同服务其他实例的广播）→ 放行分发.
     * 多实例互通的核心回归（SSO A 实例踢人广播可达 B 实例）.
     */
    @Test
    void sameServiceOtherInstanceIsDispatchedInInstanceMode() {
        listener.onMessage(message(SELF_NAME, "other-instance"));

        verify(publisher).publishEvent(any(MeApplicationEvent.class));
    }

    /**
     * service 回程：self-filter=service 时同服务名消息全部丢弃（旧语义）.
     */
    @Test
    void sameServiceMessageIsFilteredInServiceMode() {
        properties.setSelfFilter(EventBridgeProperties.SelfFilter.SERVICE);

        listener.onMessage(message(SELF_NAME, "other-instance"));

        verifyNoInteractions(publisher);
    }

    /**
     * 来自其他服务的消息两种模式下都正常分发到本地管道.
     */
    @Test
    void messageFromOtherServiceIsDispatched() {
        listener.onMessage(message("other-service", "other-instance"));

        verify(publisher).publishEvent(any(MeApplicationEvent.class));
    }

    private static EventBridgeMessage message(String sourceService, String sourceInstanceId) {
        return new EventBridgeMessage("test:event", "\"hello\"", sourceService, sourceInstanceId,
                null, null, null, Instant.now());
    }

    /**
     * 测试用事件类型：负载为字符串，还原为匿名本地事件.
     */
    private static final class StubEventType implements IEventType<String> {

        @Override
        public String type() {
            return "test:event";
        }

        @Override
        public Class<String> payloadClass() {
            return String.class;
        }

        @Override
        public MeApplicationEvent toLocalEvent(String payload, String source, String sourceInstanceId) {
            return new MeApplicationEvent(payload) {
                @Override
                public String getEventType() {
                    return type();
                }
            };
        }
    }
}
