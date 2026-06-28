package com.frame.me.ws.mvc.handler;

import com.frame.me.ws.mvc.config.WsMvcProperties;
import com.frame.me.ws.mvc.core.WsMvcSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * {@link MeWsMvcHandler} 单元测试.
 *
 * @author frame-me
 */
class MeWsMvcHandlerTest {

    private WsMvcSessionManager sessionManager;
    private WsMvcProperties properties;
    private MeWsMvcHandler handler;

    @BeforeEach
    void setUp() {
        sessionManager = mock(WsMvcSessionManager.class);
        properties = new WsMvcProperties();
        handler = new MeWsMvcHandler(sessionManager, properties);
    }

    @Test
    void shouldRegisterBroadcastOnConnection() throws Exception {
        WebSocketSession session = mockSession("ws://localhost/me/ws?type=broadcast&eventType=user:created");

        handler.afterConnectionEstablished(session);

        verify(sessionManager).registerBroadcast(session, "user:created");
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    void shouldRegisterTargetedOnConnection() throws Exception {
        WebSocketSession session = mockSession("ws://localhost/me/ws?type=targeted&receiverId=user:123");

        handler.afterConnectionEstablished(session);

        verify(sessionManager).registerTargeted(session, "user:123");
    }

    @Test
    void shouldCloseWhenSubscribeTypeInvalid() throws Exception {
        WebSocketSession session = mockSession("ws://localhost/me/ws?type=unknown");

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.BAD_DATA);
        verify(sessionManager, never()).registerBroadcast(any(), any());
    }

    @Test
    void shouldCloseWhenBroadcastDisabled() throws Exception {
        properties.setBroadcastEnabled(false);
        WebSocketSession session = mockSession("ws://localhost/me/ws?type=broadcast&eventType=user:created");

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void shouldRespondPongToPing() throws Exception {
        WebSocketSession session = mockSession("ws://localhost/me/ws?type=broadcast&eventType=user:created");

        handler.handleTextMessage(session, new TextMessage("ping"));

        verify(session).sendMessage(new TextMessage("pong"));
    }

    @Test
    void shouldRemoveSessionOnClose() throws Exception {
        WebSocketSession session = mockSession("ws://localhost/me/ws?type=broadcast&eventType=user:created");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionManager).removeSession(session);
    }

    private WebSocketSession mockSession(String uri) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.getUri()).thenReturn(URI.create(uri));
        return session;
    }
}
