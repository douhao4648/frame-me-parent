package com.frame.me.ws.mvc.service;

import com.frame.me.ws.mvc.core.WsMvcPayload;
import com.frame.me.ws.mvc.core.WsMvcSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link WsMvcPushService} 单元测试.
 *
 * @author frame-me
 */
class WsMvcPushServiceTest {

    private WsMvcSessionManager sessionManager;
    private WsMvcPushService pushService;

    @BeforeEach
    void setUp() {
        sessionManager = mock(WsMvcSessionManager.class);
        pushService = new WsMvcPushService(sessionManager);
    }

    @Test
    void shouldBroadcastViaManager() {
        when(sessionManager.broadcast(eq("user:created"), any(WsMvcPayload.class))).thenReturn(3);

        int sent = pushService.broadcast("user:created", "hello");

        assertThat(sent).isEqualTo(3);
        verify(sessionManager).broadcast(eq("user:created"), any(WsMvcPayload.class));
    }

    @Test
    void shouldPushToReceiverViaManager() {
        when(sessionManager.pushToReceiver(eq("user:123"), any(WsMvcPayload.class))).thenReturn(1);

        int sent = pushService.pushToReceiver("user:123", "notification", "hi");

        assertThat(sent).isEqualTo(1);
        verify(sessionManager).pushToReceiver(eq("user:123"), any(WsMvcPayload.class));
    }

    @Test
    void shouldPushToReceiverWithDefaultEventType() {
        when(sessionManager.pushToReceiver(eq("user:123"), any(WsMvcPayload.class))).thenReturn(1);

        pushService.pushToReceiver("user:123", "hi");

        verify(sessionManager).pushToReceiver(eq("user:123"), argThat(p -> "message".equals(p.getEventType())));
    }
}
