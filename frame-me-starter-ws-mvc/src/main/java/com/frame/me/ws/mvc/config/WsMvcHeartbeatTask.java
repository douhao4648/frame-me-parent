package com.frame.me.ws.mvc.config;

import com.frame.me.ws.mvc.core.WsMvcSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket MVC 心跳任务.
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class WsMvcHeartbeatTask {

    private final WsMvcSessionManager sessionManager;
    private final WsMvcProperties properties;

    /**
     * 定时发送 ping.
     */
    @Scheduled(fixedRateString = "#{${me.ws.mvc.heartbeat-interval:0} * 1000}")
    public void heartbeat() {
        for (WebSocketSession session : sessionManager.getAllSessions()) {
            if (!session.isOpen()) {
                sessionManager.removeSession(session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage("ping"));
            } catch (Exception e) {
                log.debug("WebSocket heartbeat failed, remove session {}: {}", session.getId(), e.getMessage());
                sessionManager.removeSession(session);
            }
        }
    }
}
