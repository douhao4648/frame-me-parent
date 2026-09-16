package com.frame.me.base.event;

import com.alibaba.fastjson2.JSON;
import com.frame.me.event.AbstractMeApplicationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

/**
 * 事件桥接发布器.
 *
 * <p>统一入口：先发布本地事件，再按事件类型选择 transport 广播到跨服务通道。
 * 订阅了同一通道的其他服务实例会收到消息并还原为本地事件。</p>
 *
 * <p><b>本地发布与远程发送非原子</b>：{@link #publish} 先同步执行本地 {@code @EventListener}
 * （可能已写库、发通知），再 {@code transport.send} 广播。远程发送失败时本地副作用已产生、
 * 无法回滚。当前 Redis Pub/Sub transport 是 best-effort、at-most-once，订阅方离线或处理失败都会丢消息；
 * 需要可靠送达时应使用事务 Outbox + 支持确认/重试的持久化消息通道。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class EventBridgePublisher {

    private final ApplicationEventPublisher localPublisher;
    private final EventBridgeProperties properties;
    private final Map<String, IEventTransport> transports;

    /**
     * 发布事件.
     *
     * @param event 本地事件
     */
    public void publish(AbstractMeApplicationEvent event) {
        // 1. 本地发布（同进程内所有 @EventListener 立即收到）
        localPublisher.publishEvent(event);

        // 2. 若不允许广播，直接结束
        if (!event.isBroadcast()) {
            log.debug("Event broadcast disabled for type: {}", event.getEventType());
            return;
        }

        String type = event.getEventType();
        String transportName = properties.resolveTransport(type);
        IEventTransport transport = transports.get(transportName);
        if (transport == null) {
            log.warn("No IEventTransport bean named '{}' found for event type: {}", transportName, type);
            return;
        }

        String payload = JSON.toJSONString(event.getPayload());
        EventBridgeMessage message = EventBridgeMessage.of(type, payload, properties.getServiceName(),
                properties.getInstanceId(), event.getEventId(), event.getTargetService(), event.getTargetId());
        transport.send(type, message);
        log.debug("Event broadcast via {}: type={}, sourceInstanceId={}, targetService={}, targetId={}",
                transportName, type, properties.getInstanceId(), event.getTargetService(), event.getTargetId());
    }
}
