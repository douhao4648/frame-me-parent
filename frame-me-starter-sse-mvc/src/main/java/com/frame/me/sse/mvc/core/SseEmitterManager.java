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
        return new SseEmitter(properties.getTimeout());
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
        broadcastEmitters.forEach((type, list) -> {
            list.remove(emitter);
            if (list.isEmpty()) {
                broadcastEmitters.remove(type);
            }
        });
        String receiverId = emitterToReceiver.remove(emitter);
        if (receiverId != null) {
            Set<SseEmitter> set = targetedEmitters.get(receiverId);
            if (set != null) {
                set.remove(emitter);
                if (set.isEmpty()) {
                    targetedEmitters.remove(receiverId);
                }
            }
        }
    }
}
