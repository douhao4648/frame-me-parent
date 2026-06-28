package com.frame.me.redis.event;

import com.frame.me.base.event.EventTransport;
import com.frame.me.event.EventBridgeMessage;
import com.frame.me.redis.util.RedissonTopic;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.listener.MessageListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 基于 Redis Pub/Sub 的事件传输实现.
 *
 * <p>使用 {@link RedissonTopic} 完成 Topic 发布与订阅，
 * Bean 名称为 {@code redisEventTransport}。</p>
 *
 * @author frame-me
 */
@Slf4j
public class RedisEventTransport implements EventTransport, MessageListener<EventBridgeMessage> {

    private final String topicPrefix;
    private final Map<String, Consumer<EventBridgeMessage>> dispatchers = new ConcurrentHashMap<>();

    /**
     * 创建 Redis 传输实现.
     *
     * @param topicPrefix Topic 前缀
     */
    public RedisEventTransport(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    @Override
    public void send(String type, EventBridgeMessage message) {
        String topic = topicPrefix + type;
        long clients = RedissonTopic.topicPublish(topic, message);
        log.debug("Redis event published: type={}, topic={}, clients={}", type, topic, clients);
    }

    @Override
    public void subscribe(String type, Consumer<EventBridgeMessage> dispatcher) {
        dispatchers.put(type, dispatcher);
        String topic = topicPrefix + type;
        RedissonTopic.topicSubscribe(topic, EventBridgeMessage.class, this);
        log.debug("Redis event subscribed: type={}, topic={}", type, topic);
    }

    @Override
    public void onMessage(CharSequence channel, EventBridgeMessage message) {
        String type = message.getType();
        Consumer<EventBridgeMessage> dispatcher = dispatchers.get(type);
        if (dispatcher != null) {
            dispatcher.accept(message);
        } else {
            log.warn("No dispatcher for Redis message type: {}", type);
        }
    }
}
