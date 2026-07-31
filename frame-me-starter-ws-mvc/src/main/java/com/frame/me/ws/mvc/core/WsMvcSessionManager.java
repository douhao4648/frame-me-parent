package com.frame.me.ws.mvc.core;

import com.alibaba.fastjson2.JSON;
import com.frame.me.base.event.IReceiverIdAuthorizer;
import com.frame.me.ws.mvc.WsMvcConstant;
import com.frame.me.ws.mvc.config.WsMvcProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * WebSocket Session 生命周期与路由管理.
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class WsMvcSessionManager {

    private final WsMvcProperties properties;
    /**
     * 可选的 receiverId 授权器：业务方注册 {@link IReceiverIdAuthorizer} Bean 后，
     * 定向订阅时会校验 receiverId 与当前登录身份的归属，未注册则不校验（保持兼容）.
     */
    private final Optional<IReceiverIdAuthorizer> receiverIdAuthorizer;

    private final Map<String, Set<WebSocketSession>> broadcastSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> targetedSessions = new ConcurrentHashMap<>();
    private final Map<String, SessionMetadata> sessionMetadata = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> allSessions = ConcurrentHashMap.newKeySet();

    private static final Pattern SAFE_ID = Pattern.compile(WsMvcConstant.SAFE_ID_PATTERN);

    /**
     * 注册广播订阅 Session.
     * <p>
     * 注册时以 {@link ConcurrentWebSocketSessionDecorator} 包装原始 session：
     * 心跳、广播、pong 等多线程发送由此串行化，避免帧交错（TEXT_PARTIAL_WRITABLE），
     * 并受 {@code me.ws.mvc.send-time-limit} / {@code buffer-size-limit} 防护慢客户端。
     *
     * @param session   WebSocket session
     * @param eventType 事件类型
     */
    public void registerBroadcast(WebSocketSession session, String eventType) {
        validateId(eventType, "eventType");
        checkSessionLimit();
        WebSocketSession decorated = decorate(session);
        allSessions.add(decorated);
        broadcastSessions.computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet()).add(decorated);
        sessionMetadata.put(session.getId(), new SessionMetadata(session.getId(), decorated,
                WsMvcConstant.SUBSCRIBE_BROADCAST, eventType, null));
    }

    /**
     * 注册定向订阅 Session.
     *
     * @param session    WebSocket session
     * @param receiverId 接收者标识
     */
    public void registerTargeted(WebSocketSession session, String receiverId) {
        validateId(receiverId, "receiverId");
        authorizeReceiverId(receiverId);
        checkSessionLimit();
        WebSocketSession decorated = decorate(session);
        allSessions.add(decorated);
        targetedSessions.computeIfAbsent(receiverId, k -> ConcurrentHashMap.newKeySet()).add(decorated);
        sessionMetadata.put(session.getId(), new SessionMetadata(session.getId(), decorated,
                WsMvcConstant.SUBSCRIBE_TARGETED, null, receiverId));
    }

    /**
     * 移除 Session（按 id 匹配，注册时的原始 session 与包装后的装饰实例均可传入）.
     *
     * @param session WebSocket session
     */
    public void removeSession(WebSocketSession session) {
        SessionMetadata metadata = sessionMetadata.remove(session.getId());
        if (metadata == null) {
            return;
        }
        WebSocketSession registered = metadata.getSession();
        allSessions.remove(registered);
        if (WsMvcConstant.SUBSCRIBE_BROADCAST.equals(metadata.getSubscribeType())) {
            removeFromMap(broadcastSessions, metadata.getEventType(), registered);
        } else {
            removeFromMap(targetedSessions, metadata.getReceiverId(), registered);
        }
    }

    /**
     * 按 session id 查找已注册的（装饰后）Session，用于 handler 内回复消息.
     *
     * @param sessionId session id
     * @return 装饰后的 session，未注册返回 null
     */
    public WebSocketSession findSession(String sessionId) {
        SessionMetadata metadata = sessionMetadata.get(sessionId);
        return metadata == null ? null : metadata.getSession();
    }

    private WebSocketSession decorate(WebSocketSession session) {
        return new ConcurrentWebSocketSessionDecorator(session,
                properties.getSendTimeLimit(), properties.getBufferSizeLimit());
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

    /**
     * 返回所有会话的快照，供心跳/监控等只读遍历使用.
     *
     * <p>返回的列表独立于内部并发集合，调用方可安全遍历而不受并发修改影响。</p>
     */
    public List<WebSocketSession> getAllSessionsSnapshot() {
        return List.copyOf(allSessions);
    }

    private void checkSessionLimit() {
        int max = properties.getMaxSessions();
        if (max > 0 && allSessions.size() >= max) {
            // Handler 捕获后以 CloseStatus.TRY_AGAIN_LATER 优雅关闭，而非抛异常导致连接异常断开
            throw new IllegalStateException("session-limit-reached:" + max);
        }
    }

    /**
     * 校验 eventType / receiverId：非空、长度 ≤ {@link WsMvcConstant#MAX_ID_LENGTH}、
     * 仅含安全字符，防恶意超长或非法 key 撑爆 ConcurrentHashMap / 触发异常.
     *
     * @throws IllegalArgumentException 校验失败，Handler 捕获后以 CloseStatus.BAD_DATA 关闭连接
     */
    private void validateId(String id, String name) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        if (id.length() > WsMvcConstant.MAX_ID_LENGTH) {
            throw new IllegalArgumentException(name + " 长度超过 " + WsMvcConstant.MAX_ID_LENGTH);
        }
        if (!SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(name + " 含非法字符，仅允许字母数字、冒号、下划线、短横");
        }
    }

    /**
     * 业务方注册了 {@link IReceiverIdAuthorizer} 时，校验当前请求是否有权订阅该 receiverId，
     * 失败抛 {@link IllegalArgumentException}（Handler 捕获后以 BAD_DATA 关闭连接），
     * 防越权订阅他人事件；未注册则跳过（由业务方自行保护）.
     */
    private void authorizeReceiverId(String receiverId) {
        if (receiverIdAuthorizer.isPresent()
                && !receiverIdAuthorizer.get().authorize(receiverId)) {
            throw new IllegalArgumentException("无权订阅 receiverId: " + receiverId);
        }
    }

    /**
     * 从分类 Map 原子地移除一个 session.
     *
     * <p>用 {@code compute} 把"移除元素 + 判空移除 key"收敛到同一个原子段，
     * 避免 remove-then-removeKey 两步之间新连接复用被清空的空 Set 而丢失.</p>
     */
    private void removeFromMap(Map<String, Set<WebSocketSession>> map, String key, WebSocketSession session) {
        if (key == null) {
            return;
        }
        map.compute(key, (k, set) -> {
            if (set == null) {
                return null;
            }
            set.remove(session);
            return set.isEmpty() ? null : set;
        });
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
        private WebSocketSession session;
        private String subscribeType;
        private String eventType;
        private String receiverId;
    }
}
