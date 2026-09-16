package com.frame.me.redis.util;

import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.PatternMessageListener;

import java.util.Objects;

/**
 * Redisson 消息与发布订阅工具类.
 *
 * <p>封装 Topic、PatternTopic、ReliableTopic、Stream 等消息能力，
 * 提供原始对象获取与常用便捷方法。</p>
 */
public final class RedissonTopic {

    private final RedissonClient redissonClient;

    public RedissonTopic(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient");
    }

    // ============================ Topic ============================

    /**
     * 获取 Topic.
     *
     * @param key 键
     * @return RTopic
     */
    public RTopic getTopic(String key) {
        return redissonClient.getTopic(key);
    }

    /**
     * 发布消息.
     *
     * @param key     键
     * @param message 消息
     * @return 接收到的客户端数量
     */
    public long topicPublish(String key, Object message) {
        return getTopic(key).publish(message);
    }

    /**
     * 订阅消息.
     *
     * @param key      键
     * @param type     消息类型
     * @param listener 监听器
     * @param <T>      消息类型
     * @return 监听器 ID
     */
    public <T> int topicSubscribe(String key, Class<T> type, MessageListener<T> listener) {
        return getTopic(key).addListener(type, listener);
    }

    /**
     * 移除监听器.
     *
     * @param key        键
     * @param listenerId 监听器 ID
     */
    public void topicUnsubscribe(String key, int listenerId) {
        getTopic(key).removeListener(listenerId);
    }

    /**
     * 移除监听器（String 类型 ID 重载，用于 {@link #reliableTopicSubscribe} 返回的监听器 ID）.
     *
     * @param key        键
     * @param listenerId 监听器 ID（来自 {@code reliableTopicSubscribe} 的返回值）
     */
    public void topicUnsubscribe(String key, String listenerId) {
        getReliableTopic(key).removeListener(listenerId);
    }

    // ============================ PatternTopic ============================

    /**
     * 获取模式 Topic.
     *
     * @param pattern 模式
     * @return RPatternTopic
     */
    public RPatternTopic getPatternTopic(String pattern) {
        return redissonClient.getPatternTopic(pattern);
    }

    /**
     * 订阅模式消息.
     *
     * @param pattern  模式
     * @param type     消息类型
     * @param listener 监听器
     * @param <T>      消息类型
     * @return 监听器 ID
     */
    public <T> int patternTopicSubscribe(String pattern, Class<T> type,
                                                PatternMessageListener<T> listener) {
        return getPatternTopic(pattern).addListener(type, listener);
    }

    // ============================ ReliableTopic ============================

    /**
     * 获取可靠 Topic.
     *
     * @param key 键
     * @return RReliableTopic
     */
    public RReliableTopic getReliableTopic(String key) {
        return redissonClient.getReliableTopic(key);
    }

    /**
     * 发布可靠消息.
     *
     * @param key     键
     * @param message 消息
     * @return 接收到的客户端数量
     */
    public long reliableTopicPublish(String key, Object message) {
        return getReliableTopic(key).publish(message);
    }

    /**
     * 订阅可靠消息.
     *
     * @param key      键
     * @param type     消息类型
     * @param listener 监听器
     * @param <T>      消息类型
     * @return 监听器 ID
     */
    public <T> String reliableTopicSubscribe(String key, Class<T> type, MessageListener<T> listener) {
        return getReliableTopic(key).addListener(type, listener);
    }

    // ============================ Stream ============================

    /**
     * 获取 Stream.
     *
     * <p>Redis 5.0+ 支持的日志型数据结构。如需添加消息、创建消费组等操作，
     * 请使用 {@link RStream#add(org.redisson.api.stream.StreamAddArgs)}、
     * {@link RStream#createGroup(org.redisson.api.stream.StreamCreateGroupArgs)} 等原始 API。</p>
     *
     * @param key 键
     * @return RStream
     */
    public RStream<Object, Object> getStream(String key) {
        return redissonClient.getStream(key);
    }
}
