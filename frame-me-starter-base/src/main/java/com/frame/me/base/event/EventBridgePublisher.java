package com.frame.me.base.event;

import com.alibaba.fastjson2.JSON;
import com.frame.me.event.EventBridgeMessage;
import com.frame.me.event.MeApplicationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

/**
 * 事件桥接发布器.
 *
 * <p>统一入口：先发布本地 {@link ApplicationEvent}，再按事件类型选择 transport 广播到跨服务通道。
 * 订阅了同一通道的其他服务实例会收到消息并还原为本地事件。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class EventBridgePublisher {

    private final ApplicationEventPublisher localPublisher;
    private final EventBridgeProperties properties;
    private final Map<String, EventTransport> transports;

    /**
     * 发布事件.
     *
     * @param event 本地事件
     */
    public void publish(MeApplicationEvent event) {
        // 1. 本地发布（同进程内所有 @EventListener 立即收到）
        localPublisher.publishEvent(event);

        // 2. 若不允许广播，直接结束
        if (!event.isBroadcast()) {
            log.debug("Event broadcast disabled for type: {}", event.getEventType());
            return;
        }

        String type = event.getEventType();
        String transportName = properties.resolveTransport(type);
        EventTransport transport = transports.get(transportName);
        if (transport == null) {
            log.warn("No EventTransport bean named '{}' found for event type: {}", transportName, type);
            return;
        }

        String payload = JSON.toJSONString(event.getPayload());
        EventBridgeMessage message = EventBridgeMessage.of(type, payload, properties.getServiceName());
        transport.send(type, message);
        log.debug("Event broadcast via {}: type={}", transportName, type);
    }
}
