package com.frame.me.ws.mvc.handler;

import com.frame.me.ws.mvc.WsMvcConstant;
import com.frame.me.ws.mvc.config.WsMvcProperties;
import com.frame.me.ws.mvc.core.WsMvcSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Servlet 原生 WebSocket Handler.
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class MeWsMvcHandler extends TextWebSocketHandler {

    private final WsMvcSessionManager sessionManager;
    private final WsMvcProperties properties;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> params = parseQueryParams(session.getUri());
        String subscribeType = params.get(WsMvcConstant.FIELD_SUBSCRIBE_TYPE);

        if (WsMvcConstant.SUBSCRIBE_BROADCAST.equalsIgnoreCase(subscribeType)) {
            String eventType = params.get(WsMvcConstant.FIELD_EVENT_TYPE);
            if (eventType == null || eventType.isBlank()) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }
            if (!properties.isBroadcastEnabled()) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            sessionManager.registerBroadcast(session, eventType);
            log.debug("WS broadcast subscribe: session={}, eventType={}", session.getId(), eventType);
        } else if (WsMvcConstant.SUBSCRIBE_TARGETED.equalsIgnoreCase(subscribeType)) {
            String receiverId = params.get(WsMvcConstant.FIELD_RECEIVER_ID);
            if (receiverId == null || receiverId.isBlank()) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }
            if (!properties.isTargetedEnabled()) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            sessionManager.registerTargeted(session, receiverId);
            log.debug("WS targeted subscribe: session={}, receiverId={}", session.getId(), receiverId);
        } else {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
        // 未来可扩展 JSON 命令协议（动态换订、ack 等）
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.removeSession(session);
        log.debug("WS disconnected: session={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WS transport error: session={}, error={}", session.getId(), exception.getMessage());
        sessionManager.removeSession(session);
    }

    private Map<String, String> parseQueryParams(URI uri) {
        if (uri == null) {
            return Map.of();
        }
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap();
    }
}
