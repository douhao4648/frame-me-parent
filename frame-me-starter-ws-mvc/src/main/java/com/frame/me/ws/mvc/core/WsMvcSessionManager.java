package com.frame.me.ws.mvc.core;

import com.alibaba.fastjson2.JSON;
import com.frame.me.ws.mvc.WsMvcConstant;
import com.frame.me.ws.mvc.config.WsMvcProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Session 生命周期与路由管理.
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class WsMvcSessionManager {

    private final WsMvcProperties properties;

    private final Map<String, Set<WebSocketSession>> broadcastSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> targetedSessions = new ConcurrentHashMap<>();
    private final Map<String, SessionMetadata> sessionMetadata = new ConcurrentHashMap<>();
    @Getter
    private final Set<WebSocketSession> allSessions = ConcurrentHashMap.newKeySet();

    /**
     * 注册广播订阅 Session.
     *
     * @param session   WebSocket session
     * @param eventType 事件类型
     */
    public void registerBroadcast(WebSocketSession session, String eventType) {
        checkSessionLimit();
        allSessions.add(session);
        broadcastSessions.computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionMetadata.put(session.getId(), new SessionMetadata(session.getId(),
                WsMvcConstant.SUBSCRIBE_BROADCAST, eventType, null));
    }

    /**
     * 注册定向订阅 Session.
     *
     * @param session    WebSocket session
     * @param receiverId 接收者标识
     */
    public void registerTargeted(WebSocketSession session, String receiverId) {
        checkSessionLimit();
        allSessions.add(session);
        targetedSessions.computeIfAbsent(receiverId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionMetadata.put(session.getId(), new SessionMetadata(session.getId(),
                WsMvcConstant.SUBSCRIBE_TARGETED, null, receiverId));
    }

    /**
     * 移除 Session.
     *
     * @param session WebSocket session
     */
    public void removeSession(WebSocketSession session) {
        if (!allSessions.remove(session)) {
            return;
        }
        SessionMetadata metadata = sessionMetadata.remove(session.getId());
        if (metadata == null) {
            return;
        }
        if (WsMvcConstant.SUBSCRIBE_BROADCAST.equals(metadata.getSubscribeType())) {
            removeFromMap(broadcastSessions, metadata.getEventType(), session);
        } else {
            removeFromMap(targetedSessions, metadata.getReceiverId(), session);
        }
    }

    /**
     * 向指定事件类型的所有广播订阅者推送.
     *
     * @param eventType 事件类型
     * @param payload   消息体
     * @return 成功发送的客户端数
     */
    public int broadcast(String eventType, WsMvcPayload payload) {
        Set<WebSocketSession> sessions = broadcastSessions.get(eventType);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        String json = JSON.toJSONString(payload);
        int success = 0;
        for (WebSocketSession session : sessions) {
            if (send(session, json)) {
                success++;
            }
        }
        return success;
    }

    /**
     * 向指定接收者的所有订阅 Session 推送.
     *
     * @param receiverId 接收者标识
     * @param payload    消息体
     * @return 成功发送的客户端数
     */
    public int pushToReceiver(String receiverId, WsMvcPayload payload) {
        Set<WebSocketSession> sessions = targetedSessions.get(receiverId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        String json = JSON.toJSONString(payload);
        int success = 0;
        for (WebSocketSession session : sessions) {
            if (send(session, json)) {
                success++;
            }
        }
        return success;
    }

    public int broadcastChannelCount() {
        return broadcastSessions.size();
    }

    public int targetedReceiverCount() {
        return targetedSessions.size();
    }

    public int activeSessionCount() {
        return allSessions.size();
    }

    private void checkSessionLimit() {
        int max = properties.getMaxSessions();
        if (max > 0 && allSessions.size() >= max) {
            throw new IllegalStateException("WebSocket session limit reached: " + max);
        }
    }

    private void removeFromMap(Map<String, Set<WebSocketSession>> map, String key, WebSocketSession session) {
        if (key == null) {
            return;
        }
        Set<WebSocketSession> set = map.get(key);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                map.remove(key);
            }
        }
    }

    private boolean send(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            removeSession(session);
            return false;
        }
        try {
            session.sendMessage(new TextMessage(json));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("WebSocket send failed, remove session {}: {}", session.getId(), e.getMessage());
            removeSession(session);
            return false;
        }
    }

    /**
     * Session 元数据.
     */
    @Data
    @AllArgsConstructor
    public static class SessionMetadata {
        private String sessionId;
        private String subscribeType;
        private String eventType;
        private String receiverId;
    }
}
