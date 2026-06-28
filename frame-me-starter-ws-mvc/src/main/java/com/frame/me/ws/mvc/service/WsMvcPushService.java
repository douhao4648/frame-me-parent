package com.frame.me.ws.mvc.service;

import com.frame.me.ws.mvc.core.WsMvcPayload;
import com.frame.me.ws.mvc.core.WsMvcSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket MVC 业务推送入口.
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class WsMvcPushService {

    private final WsMvcSessionManager sessionManager;

    /**
     * 向指定事件类型的所有广播订阅者推送消息.
     *
     * @param eventType 事件类型
     * @param data      业务数据
     * @return 成功发送的客户端数
     */
    public int broadcast(String eventType, Object data) {
        WsMvcPayload payload = WsMvcPayload.of(eventType, data);
        return sessionManager.broadcast(eventType, payload);
    }

    /**
     * 向指定接收者定向推送消息.
     *
     * @param receiverId 接收者标识
     * @param eventType  事件类型
     * @param data       业务数据
     * @return 成功发送的客户端数
     */
    public int pushToReceiver(String receiverId, String eventType, Object data) {
        WsMvcPayload payload = WsMvcPayload.of(eventType, data, receiverId);
        return sessionManager.pushToReceiver(receiverId, payload);
    }

    /**
     * 向指定接收者定向推送消息（默认事件类型 message）.
     *
     * @param receiverId 接收者标识
     * @param data       业务数据
     * @return 成功发送的客户端数
     */
    public int pushToReceiver(String receiverId, Object data) {
        return pushToReceiver(receiverId, "message", data);
    }
}
