package com.frame.me.base.event;

import com.frame.me.base.event.EventBridgeMessage;
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
 * {@link EventBridgeListener} 自过滤单元测试.
 *
 * @author frame-me
 */
class EventBridgeListenerTest {

    private static final String SELF_NAME = "unknown-abc12345";

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private EventBridgeListener listener;

    @BeforeEach
    void setUp() {
        EventBridgeProperties properties = new EventBridgeProperties();
        properties.setServiceName(SELF_NAME);
        listener = new EventBridgeListener(publisher, properties, Map.of());
        listener.register(new StubEventType());
    }

    /**
     * 服务名为生成的唯一名时，自过滤生效：自身回声消息不再触发本地二次分发.
     */
    @Test
    void selfProducedMessageIsFilteredWithGeneratedUniqueName() {
        listener.onMessage(message(SELF_NAME));

        verifyNoInteractions(publisher);
    }

    /**
     * 来自其他服务的消息正常分发到本地管道.
     */
    @Test
    void messageFromOtherServiceIsDispatched() {
        listener.onMessage(message("other-service"));

        verify(publisher).publishEvent(any(MeApplicationEvent.class));
    }

    private static EventBridgeMessage message(String sourceService) {
        return new EventBridgeMessage("test:event", "\"hello\"", sourceService, null, null, Instant.now());
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
        public MeApplicationEvent toLocalEvent(String payload, String source) {
            return new MeApplicationEvent(payload) {
                @Override
                public String getEventType() {
                    return type();
                }
            };
        }
    }
}
