package com.frame.me.redis.event;

import com.frame.me.event.EventBridgeMessage;
import com.frame.me.redis.util.RedissonTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisEventTransport} 单元测试.
 *
 * @author frame-me
 */
@SuppressWarnings("unchecked")
class RedisEventTransportTest {

    @AfterEach
    void tearDown() {
        RedissonTopic.init(null);
    }

    @Test
    void shouldPublishMessageToTopicWithPrefix() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(client.getTopic("me:event:user:created")).thenReturn(topic);
        RedissonTopic.init(client);

        RedisEventTransport transport = new RedisEventTransport("me:event:");
        EventBridgeMessage message = EventBridgeMessage.of("user:created", "{}", "svc-a");
        transport.send("user:created", message);

        verify(topic).publish(message);
    }

    @Test
    void shouldSubscribeTopicWithPrefixAndDispatchMessage() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(client.getTopic("me:event:user:created")).thenReturn(topic);
        RedissonTopic.init(client);

        RedisEventTransport transport = new RedisEventTransport("me:event:");
        Consumer<EventBridgeMessage> dispatcher = mock(Consumer.class);
        transport.subscribe("user:created", dispatcher);

        verify(topic).addListener(eq(EventBridgeMessage.class), any(MessageListener.class));

        EventBridgeMessage message = EventBridgeMessage.of("user:created", "{}", "svc-b");
        transport.onMessage("me:event:user:created", message);

        verify(dispatcher).accept(message);
    }

    @Test
    void shouldUseTypeFromMessageToSelectDispatcher() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topicA = mock(RTopic.class);
        RTopic topicB = mock(RTopic.class);
        when(client.getTopic("me:event:type:a")).thenReturn(topicA);
        when(client.getTopic("me:event:type:b")).thenReturn(topicB);
        RedissonTopic.init(client);

        RedisEventTransport transport = new RedisEventTransport("me:event:");
        Consumer<EventBridgeMessage> dispatcherA = mock(Consumer.class);
        Consumer<EventBridgeMessage> dispatcherB = mock(Consumer.class);
        transport.subscribe("type:a", dispatcherA);
        transport.subscribe("type:b", dispatcherB);

        EventBridgeMessage messageA = EventBridgeMessage.of("type:a", "{}", "svc");
        EventBridgeMessage messageB = EventBridgeMessage.of("type:b", "{}", "svc");
        transport.onMessage("me:event:type:a", messageA);
        transport.onMessage("me:event:type:b", messageB);

        verify(dispatcherA).accept(messageA);
        verify(dispatcherB).accept(messageB);
        assertThat(transport).isNotNull();
    }
}
