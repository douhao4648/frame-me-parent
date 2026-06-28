package com.frame.me.sse.mvc.service;

import com.frame.me.sse.mvc.core.SseEmitterManager;
import com.frame.me.sse.mvc.core.SsePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SSE 业务推送入口.
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class SsePushService {

    private final SseEmitterManager emitterManager;

    /**
     * 向指定事件类型的所有广播订阅者推送消息.
     *
     * @param eventType 事件类型
     * @param data      业务数据
     * @return 成功推送的客户端数
     */
    public int broadcast(String eventType, Object data) {
        SsePayload payload = SsePayload.of(eventType, data);
        return emitterManager.broadcast(eventType, payload);
    }

    /**
     * 向指定接收者定向推送消息.
     *
     * @param receiverId 接收者标识
     * @param eventType  事件类型
     * @param data       业务数据
     * @return 成功推送的客户端数
     */
    public int pushToReceiver(String receiverId, String eventType, Object data) {
        SsePayload payload = SsePayload.of(eventType, data, receiverId);
        return emitterManager.pushToReceiver(receiverId, payload);
    }

    /**
     * 向指定接收者定向推送消息（默认事件类型 message）.
     *
     * @param receiverId 接收者标识
     * @param data       业务数据
     * @return 成功推送的客户端数
     */
    public int pushToReceiver(String receiverId, Object data) {
        return pushToReceiver(receiverId, "message", data);
    }
}
