package com.frame.me.base.event;

import com.alibaba.fastjson2.JSON;
import com.frame.me.event.EventBridgeMessage;
import com.frame.me.event.EventType;
import com.frame.me.event.MeApplicationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件桥接监听器.
 *
 * <p>负责注册事件类型、订阅跨服务通道，并在收到 {@link EventBridgeMessage} 后根据 {@code type}
 * 查找注册器，反序列化负载并还原为本地 {@link MeApplicationEvent}，再次通过 Spring 本地管道发布。
 * 这样同服务的 {@link org.springframework.context.event.EventListener} 无需关心事件来源。</p>
 *
 * <p>启动时会自动从 Spring 上下文收集所有 {@link EventType} Bean 并注册；
 * 业务也可手动调用 {@link #register(EventType)}。</p>
 *
 * @author frame-me
 */
@Slf4j
public class EventBridgeListener implements SmartInitializingSingleton, ApplicationContextAware {

    private final ApplicationEventPublisher localPublisher;
    private final EventBridgeProperties properties;
    private final Map<String, EventTransport> transports;
    private final Map<String, EventType<?>> registry = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;

    /**
     * 创建监听器.
     *
     * @param localPublisher 本地事件发布器
     * @param properties     桥接配置
     * @param transports     transport 实现
     */
    public EventBridgeListener(ApplicationEventPublisher localPublisher,
                               EventBridgeProperties properties,
                               Map<String, EventTransport> transports) {
        this.localPublisher = localPublisher;
        this.properties = properties;
        this.transports = transports;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isEnabled()) {
            log.info("Event bridge disabled, skip auto-register event types");
            return;
        }
        if (applicationContext == null) {
            return;
        }
        applicationContext.getBeansOfType(EventType.class).values().forEach(this::register);
        log.info("Event bridge auto-registered types: {}", registry.keySet());
    }

    /**
     * 注册事件类型.
     *
     * <p>注册后会立即根据配置选择 transport 订阅对应通道。</p>
     *
     * @param eventType 事件类型定义
     */
    public void register(EventType<?> eventType) {
        String type = eventType.type();
        if (registry.putIfAbsent(type, eventType) != null) {
            return;
        }

        if (!properties.isEnabled()) {
            log.debug("Event bridge disabled, skip subscribe for type: {}", type);
            return;
        }

        String transportName = properties.resolveTransport(type);
        EventTransport transport = transports.get(transportName);
        if (transport == null) {
            log.warn("No EventTransport bean named '{}' found for event type: {}, skip subscribe",
                    transportName, type);
            return;
        }

        transport.subscribe(type, this::onMessage);
        log.debug("Subscribed transport '{}' for event type: {}", transportName, type);
    }

    /**
     * 处理来自跨服务通道的消息.
     *
     * @param message 桥接消息
     */
    public void onMessage(EventBridgeMessage message) {
        String type = message.getType();
        EventType<?> eventType = registry.get(type);
        if (eventType == null) {
            log.warn("No event type registered for: {}", type);
            return;
        }

        String sourceService = message.getSourceService();
        String currentService = properties.getServiceName();
        if (sourceService != null && !sourceService.isEmpty()
                && !"unknown".equals(currentService)
                && sourceService.equals(currentService)) {
            log.debug("Ignore self-produced event: type={}, source={}", type, sourceService);
            return;
        }

        String targetService = message.getTargetService();
        if (targetService != null && !targetService.isEmpty()
                && !"unknown".equals(currentService)
                && !targetService.equals(currentService)) {
            log.debug("Ignore event for other service: type={}, targetService={}, current={}",
                    type, targetService, currentService);
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            EventType<Object> typedEventType = (EventType<Object>) eventType;
            Object payload = JSON.parseObject(message.getPayload(), typedEventType.payloadClass());
            MeApplicationEvent localEvent = typedEventType.toLocalEvent(payload, message.getSourceService());
            localPublisher.publishEvent(localEvent);
            log.debug("Event dispatched locally: type={}, source={}", type, message.getSourceService());
        } catch (Exception e) {
            log.error("Failed to dispatch event: type={}, payload={}", type, message.getPayload(), e);
        }
    }
}
