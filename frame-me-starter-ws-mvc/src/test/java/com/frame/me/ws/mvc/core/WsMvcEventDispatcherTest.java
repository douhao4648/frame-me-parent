package com.frame.me.ws.mvc.core;

import com.frame.me.event.EventClientPermit;
import com.frame.me.event.MeApplicationEvent;
import com.frame.me.ws.mvc.config.WsMvcProperties;
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
 * {@link WsMvcEventDispatcher} 单元测试.
 *
 * @author frame-me
 */
@ExtendWith(MockitoExtension.class)
class WsMvcEventDispatcherTest {

    @Mock
    private WsMvcSessionManager sessionManager;

    @Mock
    private WsMvcProperties properties;

    @Test
    void shouldBroadcastWhenNoTargetId() {
        when(properties.isBroadcastEnabled()).thenReturn(true);

        WsMvcEventDispatcher dispatcher = new WsMvcEventDispatcher(sessionManager, properties);
        dispatcher.onApplicationEvent(new TestEvent("order:paid", null));

        verify(sessionManager).broadcast(eq("order:paid"), any(WsMvcPayload.class));
        verify(sessionManager, never()).pushToReceiver(any(), any());
    }

    @Test
    void shouldPushToReceiverWhenTargetIdPresent() {
        when(properties.isTargetedEnabled()).thenReturn(true);

        WsMvcEventDispatcher dispatcher = new WsMvcEventDispatcher(sessionManager, properties);
        dispatcher.onApplicationEvent(new TestEvent("user:notify", "user:123"));

        verify(sessionManager).pushToReceiver(eq("user:123"), any(WsMvcPayload.class));
        verify(sessionManager, never()).broadcast(any(), any());
    }

    @Test
    void shouldSkipTargetedWhenDisabled() {
        when(properties.isTargetedEnabled()).thenReturn(false);

        WsMvcEventDispatcher dispatcher = new WsMvcEventDispatcher(sessionManager, properties);
        dispatcher.onApplicationEvent(new TestEvent("user:notify", "user:123"));

        verify(sessionManager, never()).pushToReceiver(any(), any());
        verify(sessionManager, never()).broadcast(any(), any());
    }

    @Test
    void shouldSkipBroadcastWhenDisabled() {
        when(properties.isBroadcastEnabled()).thenReturn(false);

        WsMvcEventDispatcher dispatcher = new WsMvcEventDispatcher(sessionManager, properties);
        dispatcher.onApplicationEvent(new TestEvent("order:paid", null));

        verify(sessionManager, never()).broadcast(any(), any());
    }

    @Test
    void shouldSkipEventWithoutEventClientPermit() {
        WsMvcEventDispatcher dispatcher = new WsMvcEventDispatcher(sessionManager, properties);
        dispatcher.onApplicationEvent(new ForbiddenEvent("order:paid", null));

        verify(sessionManager, never()).broadcast(any(), any());
        verify(sessionManager, never()).pushToReceiver(any(), any());
    }

    @SuppressWarnings("serial")
    @EventClientPermit
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

    @SuppressWarnings("serial")
    private static class ForbiddenEvent extends MeApplicationEvent {

        private final String eventType;
        private final String targetId;

        ForbiddenEvent(String eventType, String targetId) {
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
