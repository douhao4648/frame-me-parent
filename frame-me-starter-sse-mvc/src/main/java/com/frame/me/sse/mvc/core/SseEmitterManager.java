package com.frame.me.sse.mvc.core;

import com.alibaba.fastjson2.JSON;
import com.frame.me.sse.mvc.config.SseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE Emitter 生命周期与路由管理.
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class SseEmitterManager {

    private final SseProperties properties;

    private final Map<String, List<SseEmitter>> broadcastEmitters = new ConcurrentHashMap<>();
    private final Map<String, Set<SseEmitter>> targetedEmitters = new ConcurrentHashMap<>();
    private final Map<SseEmitter, String> emitterToReceiver = new ConcurrentHashMap<>();
    private final Set<SseEmitter> activeEmitters = ConcurrentHashMap.newKeySet();

    /**
     * 注册广播订阅 Emitter.
     *
     * @param eventType 事件类型
     * @return SseEmitter
     */
    public SseEmitter registerBroadcast(String eventType) {
        checkEmitterLimit();
        SseEmitter emitter = createEmitter();
        activeEmitters.add(emitter);
        broadcastEmitters.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(emitter));
        emitter.onTimeout(() -> removeEmitter(emitter));
        emitter.onError(e -> removeEmitter(emitter));

        return emitter;
    }

    /**
     * 注册定向订阅 Emitter.
     *
     * @param receiverId 接收者标识
     * @return SseEmitter
     */
    public SseEmitter registerTargeted(String receiverId) {
        checkEmitterLimit();
        SseEmitter emitter = createEmitter();
        activeEmitters.add(emitter);
        targetedEmitters.computeIfAbsent(receiverId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitterToReceiver.put(emitter, receiverId);

        emitter.onCompletion(() -> removeEmitter(emitter));
        emitter.onTimeout(() -> removeEmitter(emitter));
        emitter.onError(e -> removeEmitter(emitter));

        return emitter;
    }

    /**
     * 向指定事件类型的所有广播订阅者推送.
     *
     * @param eventType 事件类型
     * @param payload   消息体
     * @return 成功推送的客户端数
     */
    public int broadcast(String eventType, SsePayload payload) {
        List<SseEmitter> emitters = broadcastEmitters.get(eventType);
        if (emitters == null || emitters.isEmpty()) {
            return 0;
        }
        String json = JSON.toJSONString(payload);
        int success = 0;
        for (SseEmitter emitter : emitters) {
            if (send(emitter, eventType, json)) {
                success++;
            }
        }
        return success;
    }

    /**
     * 向指定接收者的所有订阅 Emitter 推送.
     *
     * @param receiverId 接收者标识
     * @param payload    消息体
     * @return 成功推送的客户端数
     */
    public int pushToReceiver(String receiverId, SsePayload payload) {
        Set<SseEmitter> emitters = targetedEmitters.get(receiverId);
        if (emitters == null || emitters.isEmpty()) {
            return 0;
        }
        String eventType = payload.getEventType() != null ? payload.getEventType() : "message";
        String json = JSON.toJSONString(payload);
        int success = 0;
        for (SseEmitter emitter : emitters) {
            if (send(emitter, eventType, json)) {
                success++;
            }
        }
        return success;
    }

    public int broadcastChannelCount() {
        return broadcastEmitters.size();
    }

    public int targetedReceiverCount() {
        return targetedEmitters.size();
    }

    public int activeEmitterCount() {
        return activeEmitters.size();
    }

    private void checkEmitterLimit() {
        int max = properties.getMaxEmitters();
        if (max > 0 && activeEmitters.size() >= max) {
            throw new IllegalStateException("SSE emitter limit reached: " + max);
        }
    }

    private SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(properties.getTimeout());
        // 立即发送 retry 指令：让 me.sse.retry 配置生效，客户端断线后按此间隔重连.
        try {
            emitter.send(SseEmitter.event().reconnectTime(properties.getRetry()));
        } catch (IOException e) {
            log.debug("SSE initial retry directive failed for emitter: {}", e.getMessage());
        }
        return emitter;
    }

    /**
     * 向所有存活 Emitter 发送心跳（SSE comment），发送失败即清理该 Emitter.
     *
     * <p>由 {@link com.frame.me.sse.mvc.config.SseHeartbeatTask} 定时调用，
     * 用于探测半关闭连接、保持代理/负载均衡活跃.</p>
     *
     * @return 心跳发送成功的 Emitter 数
     */
    public int heartbeat() {
        int success = 0;
        for (SseEmitter emitter : activeEmitters) {
            try {
                // SSE comment：以 ':' 开头的行是注释，客户端忽略，仅用于保活与探测.
                emitter.send(SseEmitter.event().comment("heartbeat"));
                success++;
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE heartbeat failed, remove emitter: {}", e.getMessage());
                removeEmitter(emitter);
            }
        }
        return success;
    }

    private boolean send(SseEmitter emitter, String eventName, String json) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name(eventName)
                    .data(json, MediaType.APPLICATION_JSON);
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE send failed, remove emitter: {}", e.getMessage());
            removeEmitter(emitter);
            return false;
        }
    }

    private void removeEmitter(SseEmitter emitter) {
        if (!activeEmitters.remove(emitter)) {
            return;
        }
        // 遍历中改 map 结构会触发 ConcurrentModificationException，用 compute 原子清理.
        // 每个 key 的"移除元素 + 判空移除 key"在 compute 段内原子完成，避免新连接复用被清空的空集合.
        for (String type : new java.util.ArrayList<>(broadcastEmitters.keySet())) {
            broadcastEmitters.compute(type, (k, list) -> {
                if (list == null) {
                    return null;
                }
                list.remove(emitter);
                return list.isEmpty() ? null : list;
            });
        }
        String receiverId = emitterToReceiver.remove(emitter);
        if (receiverId != null) {
            targetedEmitters.compute(receiverId, (k, set) -> {
                if (set == null) {
                    return null;
                }
                set.remove(emitter);
                return set.isEmpty() ? null : set;
            });
        }
    }
}
