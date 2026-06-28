package com.frame.me.ws.mvc.core;

import com.frame.me.ws.mvc.config.WsMvcProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * {@link WsMvcSessionManager} 单元测试.
 *
 * @author frame-me
 */
class WsMvcSessionManagerTest {

    private WsMvcSessionManager manager;
    private WsMvcProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WsMvcProperties();
        manager = new WsMvcSessionManager(properties);
    }

    @Test
    void shouldRegisterBroadcastSession() {
        WebSocketSession session = mockSession("s1");
        manager.registerBroadcast(session, "user:created");

        assertThat(manager.broadcastChannelCount()).isEqualTo(1);
        assertThat(manager.activeSessionCount()).isEqualTo(1);
    }

    @Test
    void shouldRegisterTargetedSession() {
        WebSocketSession session = mockSession("s1");
        manager.registerTargeted(session, "user:123");

        assertThat(manager.targetedReceiverCount()).isEqualTo(1);
        assertThat(manager.activeSessionCount()).isEqualTo(1);
    }

    @Test
    void shouldBroadcastToMultipleSubscribers() throws IOException {
        WebSocketSession s1 = mockSession("s1");
        WebSocketSession s2 = mockSession("s2");
        manager.registerBroadcast(s1, "user:created");
        manager.registerBroadcast(s2, "user:created");

        int sent = manager.broadcast("user:created", WsMvcPayload.of("user:created", "hello"));

        assertThat(sent).isEqualTo(2);
        verify(s1, times(1)).sendMessage(any(TextMessage.class));
        verify(s2, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldPushToTargetedReceiver() throws IOException {
        WebSocketSession s1 = mockSession("s1");
        WebSocketSession s2 = mockSession("s2");
        manager.registerTargeted(s1, "user:123");
        manager.registerTargeted(s2, "user:123");

        int sent = manager.pushToReceiver("user:123", WsMvcPayload.of("message", "hi", "user:123"));

        assertThat(sent).isEqualTo(2);
    }

    @Test
    void shouldRemoveSessionOnSendFailure() throws IOException {
        WebSocketSession session = mockSession("s1");
        doThrow(new IOException("closed")).when(session).sendMessage(any(TextMessage.class));
        manager.registerBroadcast(session, "user:created");

        int sent = manager.broadcast("user:created", WsMvcPayload.of("user:created", "hello"));

        assertThat(sent).isEqualTo(0);
        assertThat(manager.activeSessionCount()).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroWhenNoSubscribers() {
        int sent = manager.broadcast("nonexistent", WsMvcPayload.of("x", "y"));
        assertThat(sent).isEqualTo(0);
    }

    @Test
    void shouldEnforceMaxSessions() {
        properties.setMaxSessions(2);
        WsMvcSessionManager limited = new WsMvcSessionManager(properties);

        limited.registerBroadcast(mockSession("s1"), "a");
        limited.registerBroadcast(mockSession("s2"), "b");

        assertThatThrownBy(() -> limited.registerBroadcast(mockSession("s3"), "c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("limit reached");
    }

    private WebSocketSession mockSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
