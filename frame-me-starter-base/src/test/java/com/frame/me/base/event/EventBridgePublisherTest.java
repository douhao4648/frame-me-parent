package com.frame.me.base.event;

import com.frame.me.base.event.EventBridgeMessage;
import com.frame.me.event.IEventType;
import com.frame.me.event.AbstractMeApplicationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EventBridgePublisher} 单元测试.
 *
 * @author frame-me
 */
@ExtendWith(MockitoExtension.class)
class EventBridgePublisherTest {

    @Mock
    private ApplicationEventPublisher localPublisher;

    @Mock
    private IEventTransport redisTransport;

    @Mock
    private IEventTransport mqTransport;

    @Test
    void shouldPublishLocalEventAndBroadcastViaDefaultTransport() {
        EventBridgeProperties properties = new EventBridgeProperties();
        properties.setServiceName("test-service");

        Map<String, IEventTransport> transports = new HashMap<>();
        transports.put("redis", redisTransport);

        EventBridgePublisher publisher = new EventBridgePublisher(localPublisher, properties, transports);

        TestEvent event = new TestEvent(this, "user:created", "alice");
        publisher.publish(event);

        verify(localPublisher).publishEvent(event);
        ArgumentCaptor<EventBridgeMessage> captor = ArgumentCaptor.forClass(EventBridgeMessage.class);
        verify(redisTransport).send(eq("user:created"), captor.capture());

        EventBridgeMessage message = captor.getValue();
        assertThat(message.getType()).isEqualTo("user:created");
        assertThat(message.getSourceService()).isEqualTo("test-service");
        assertThat(message.getPayload()).contains("alice");
    }

    @Test
    void shouldChooseTransportByEventType() {
        EventBridgeProperties properties = new EventBridgeProperties();
        properties.setDefaultTransport("redis");
        properties.getTransports().put("order:paid", "mq");

        Map<String, IEventTransport> transports = new HashMap<>();
        transports.put("redis", redisTransport);
        transports.put("mq", mqTransport);

        EventBridgePublisher publisher = new EventBridgePublisher(localPublisher, properties, transports);

        publisher.publish(new TestEvent(this, "user:created", "bob"));
        verify(redisTransport).send(eq("user:created"), any(EventBridgeMessage.class));
        verify(mqTransport, never()).send(any(), any());

        publisher.publish(new TestEvent(this, "order:paid", "100"));
        verify(mqTransport).send(eq("order:paid"), any(EventBridgeMessage.class));
        verify(redisTransport, times(1)).send(any(), any());
    }

    @Test
    void shouldNotBroadcastWhenDisabled() {
        EventBridgeProperties properties = new EventBridgeProperties();
        Map<String, IEventTransport> transports = new HashMap<>();
        transports.put("redis", redisTransport);

        EventBridgePublisher publisher = new EventBridgePublisher(localPublisher, properties, transports);

        TestEvent event = new TestEvent(this, "user:created", "carol") {
            @Override
            public boolean isBroadcast() {
                return false;
            }
        };
        publisher.publish(event);

        verify(localPublisher).publishEvent(event);
        verify(redisTransport, never()).send(any(), any());
    }

    @Test
    void shouldSkipBroadcastWhenTransportNotFound() {
        EventBridgeProperties properties = new EventBridgeProperties();
        properties.setDefaultTransport("mq");
        Map<String, IEventTransport> transports = new HashMap<>();
        transports.put("redis", redisTransport);

        EventBridgePublisher publisher = new EventBridgePublisher(localPublisher, properties, transports);

        TestEvent event = new TestEvent(this, "user:created", "dave");
        publisher.publish(event);

        verify(localPublisher).publishEvent(event);
        verify(redisTransport, never()).send(any(), any());
    }

    @Test
    void listenerShouldDispatchRegisteredEventType() {
        EventBridgeProperties properties = new EventBridgeProperties();
        properties.setServiceName("consumer-service");
        Map<String, IEventTransport> transports = new HashMap<>();
        transports.put("redis", redisTransport);

        EventBridgeListener listener = new EventBridgeListener(localPublisher, properties, transports);
        listener.register(new TestEventType());

        verify(redisTransport).subscribe(eq("user:created"), any(Consumer.class));

        EventBridgeMessage message = EventBridgeMessage.of("user:created", "{\"value\":\"eve\"}",
                "producer-service", "producer-instance");
        listener.onMessage(message);

        ArgumentCaptor<TestEvent> captor = ArgumentCaptor.forClass(TestEvent.class);
        verify(localPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo("eve");
    }

    @Test
    void shouldCarryTargetFieldsInMessage() {
        EventBridgeProperties properties = new EventBridgeProperties();
        properties.setServiceName("producer-service");
        properties.setInstanceId("producer-instance-1");
        Map<String, IEventTransport> transports = new HashMap<>();
        transports.put("redis", redisTransport);

        EventBridgePublisher publisher = new EventBridgePublisher(localPublisher, properties, transports);

        TestEvent event = new TestEvent(this, "user:notify", "alice",
                "notification-service", "user:123");
        publisher.publish(event);

        ArgumentCaptor<EventBridgeMessage> captor = ArgumentCaptor.forClass(EventBridgeMessage.class);
        verify(redisTransport).send(eq("user:notify"), captor.capture());

        EventBridgeMessage message = captor.getValue();
        assertThat(message.getSourceInstanceId()).isEqualTo("producer-instance-1");
        assertThat(message.getTargetService()).isEqualTo("notification-service");
        assertThat(message.getTargetId()).isEqualTo("user:123");
    }

    @Test
    void listenerShouldIgnoreEventForOtherService() {
        EventBridgeProperties properties = new EventBridgeProperties();
        properties.setServiceName("consumer-service");
        Map<String, IEventTransport> transports = new HashMap<>();
        transports.put("redis", redisTransport);

        EventBridgeListener listener = new EventBridgeListener(localPublisher, properties, transports);
        listener.register(new TestEventType());

        EventBridgeMessage message = EventBridgeMessage.of("user:created", "{\"value\":\"eve\"}",
                "producer-service", "producer-instance", null, "other-service", null);
        listener.onMessage(message);

        verify(localPublisher, never()).publishEvent(any());
    }

    @Test
    void listenerShouldDispatchEventForCurrentService() {
        EventBridgeProperties properties = new EventBridgeProperties();
        properties.setServiceName("consumer-service");
        Map<String, IEventTransport> transports = new HashMap<>();
        transports.put("redis", redisTransport);

        EventBridgeListener listener = new EventBridgeListener(localPublisher, properties, transports);
        listener.register(new TestEventType());

        EventBridgeMessage message = EventBridgeMessage.of("user:created", "{\"value\":\"eve\"}",
                "producer-service", "producer-instance", null, "consumer-service", "user:123");
        listener.onMessage(message);

        ArgumentCaptor<TestEvent> captor = ArgumentCaptor.forClass(TestEvent.class);
        verify(localPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo("eve");
    }

    /**
     * 测试事件.
     */
    @SuppressWarnings("serial")
    private static class TestEvent extends AbstractMeApplicationEvent {

        private final String type;
        private final String value;
        TestEvent(Object source, String type, String value) {
            this(source, type, value, null, null);
        }

        TestEvent(Object source, String type, String value, String targetService, String targetId) {
            super(value, targetService, targetId);
            this.type = type;
            this.value = value;
        }

        @Override
        public String getEventType() {
            return type;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 测试 Payload.
     */
    @SuppressWarnings("unused")
    private static class TestPayload {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    /**
     * 测试事件类型注册项.
     */
    private static class TestEventType implements IEventType<TestPayload> {

        @Override
        public String type() {
            return "user:created";
        }

        @Override
        public Class<TestPayload> payloadClass() {
            return TestPayload.class;
        }

        @Override
        public AbstractMeApplicationEvent toLocalEvent(TestPayload payload, String source, String sourceInstanceId) {
            return new TestEvent(source, type(), payload.getValue());
        }
    }
}
