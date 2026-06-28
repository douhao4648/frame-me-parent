package com.frame.me.sse.mvc.core;

import com.frame.me.event.MeApplicationEvent;
import com.frame.me.sse.mvc.config.SseProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SseEventDispatcher} 单元测试.
 *
 * @author frame-me
 */
@ExtendWith(MockitoExtension.class)
class SseEventDispatcherTest {

    @Mock
    private SseEmitterManager emitterManager;

    @Mock
    private SseProperties properties;

    @Test
    void shouldBroadcastWhenNoTargetId() {
        when(properties.isBroadcastEnabled()).thenReturn(true);

        SseEventDispatcher dispatcher = new SseEventDispatcher(emitterManager, properties);
        dispatcher.onApplicationEvent(new TestEvent("order:paid", null));

        verify(emitterManager).broadcast(eq("order:paid"), any(SsePayload.class));
        verify(emitterManager, never()).pushToReceiver(any(), any());
    }

    @Test
    void shouldPushToReceiverWhenTargetIdPresent() {
        when(properties.isTargetedEnabled()).thenReturn(true);

        SseEventDispatcher dispatcher = new SseEventDispatcher(emitterManager, properties);
        dispatcher.onApplicationEvent(new TestEvent("user:notify", "user:123"));

        verify(emitterManager).pushToReceiver(eq("user:123"), any(SsePayload.class));
        verify(emitterManager, never()).broadcast(any(), any());
    }

    @Test
    void shouldSkipTargetedWhenDisabled() {
        when(properties.isTargetedEnabled()).thenReturn(false);

        SseEventDispatcher dispatcher = new SseEventDispatcher(emitterManager, properties);
        dispatcher.onApplicationEvent(new TestEvent("user:notify", "user:123"));

        verify(emitterManager, never()).pushToReceiver(any(), any());
        verify(emitterManager, never()).broadcast(any(), any());
    }

    @Test
    void shouldSkipBroadcastWhenDisabled() {
        when(properties.isBroadcastEnabled()).thenReturn(false);

        SseEventDispatcher dispatcher = new SseEventDispatcher(emitterManager, properties);
        dispatcher.onApplicationEvent(new TestEvent("order:paid", null));

        verify(emitterManager, never()).broadcast(any(), any());
    }

    @SuppressWarnings("serial")
    private static class TestEvent extends MeApplicationEvent {

        private final String eventType;
        private final String targetId;

        TestEvent(String eventType, String targetId) {
            super("data");
            this.eventType = eventType;
            this.targetId = targetId;
        }

        @Override
        public String getEventType() {
            return eventType;
        }

        @Override
        public String getTargetId() {
            return targetId;
        }
    }
}
