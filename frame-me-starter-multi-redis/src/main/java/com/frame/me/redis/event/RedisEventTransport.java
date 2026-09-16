package com.frame.me.redis.event;

import com.frame.me.base.event.EventBridgeMessage;
import com.frame.me.base.event.IEventTransport;
import com.frame.me.redis.util.RedissonTopic;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.listener.MessageListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 基于 Redis Pub/Sub 的事件传输实现.
 *
 * <p>使用 {@link RedissonTopic} 的普通 Redis Pub/Sub 完成 Topic 发布与订阅，
 * Bean 名称为 {@code redisEventTransport}。该通道是 best-effort、at-most-once：消息不持久化，
 * 订阅者离线、断连或处理失败时不会自动重投。</p>
 *
 * @author frame-me
 */
@Slf4j
public class RedisEventTransport implements IEventTransport, MessageListener<EventBridgeMessage> {

    private final RedissonTopic redissonTopic;
    private final String topicPrefix;
    private final Map<String, Consumer<EventBridgeMessage>> dispatchers = new ConcurrentHashMap<>();
    private final Map<String, Integer> listenerIds = new ConcurrentHashMap<>();

    /**
     * 创建 Redis 传输实现.
     *
     * @param redissonTopic Redisson Topic 客户端
     * @param topicPrefix Topic 前缀
     */
    public RedisEventTransport(RedissonTopic redissonTopic, String topicPrefix) {
        this.redissonTopic = redissonTopic;
        this.topicPrefix = topicPrefix;
    }

    @Override
    public void send(String type, EventBridgeMessage message) {
        String topic = topicPrefix + type;
        long clients = redissonTopic.topicPublish(topic, message);
        log.debug("Redis event published: type={}, topic={}, clients={}", type, topic, clients);
    }

    @Override
    public void subscribe(String type, Consumer<EventBridgeMessage> dispatcher) {
        String topic = topicPrefix + type;
        int listenerId = redissonTopic.topicSubscribe(topic, EventBridgeMessage.class, this);
        dispatchers.put(type, dispatcher);
        listenerIds.put(type, listenerId);
        log.debug("Redis event subscribed: type={}, topic={}, listenerId={}", type, topic, listenerId);
    }

    @Override
    public void unsubscribe(String type) {
        String topic = topicPrefix + type;
        Integer listenerId = listenerIds.remove(type);
        if (listenerId != null) {
            redissonTopic.topicUnsubscribe(topic, listenerId);
        }
        dispatchers.remove(type);
        log.debug("Redis event unsubscribed: type={}, topic={}", type, topic);
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
