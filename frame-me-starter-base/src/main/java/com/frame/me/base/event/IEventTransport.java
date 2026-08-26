package com.frame.me.base.event;

import java.util.function.Consumer;

/**
 * 事件传输通道抽象.
 *
 * <p>不同传输实现（Redis、MQ 等）通过该接口接入事件桥接核心。
 * 每种实现需注册为 Spring Bean，Bean 名称即为配置中使用的 transport 名称，如 {@code redisEventTransport}。</p>
 *
 * @author frame-me
 */
public interface IEventTransport {

    /**
     * 发送消息.
     *
     * @param type    事件类型
     * @param message 桥接消息
     */
    void send(String type, EventBridgeMessage message);

    /**
     * 订阅消息.
     *
     * @param type       事件类型
     * @param dispatcher 收到消息后交给桥接监听器处理
     */
    void subscribe(String type, Consumer<EventBridgeMessage> dispatcher);

    /**
     * 取消订阅.
     *
     * @param type 事件类型
     */
    default void unsubscribe(String type) {
        // 默认空实现，子类按需覆盖
    }
}
