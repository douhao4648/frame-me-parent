package com.frame.me.redis.util;

import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.PatternMessageListener;

/**
 * Redisson 消息与发布订阅工具类.
 *
 * <p>封装 Topic、PatternTopic、ReliableTopic、Stream 等消息能力，
 * 提供原始对象获取与常用便捷方法。</p>
 */
public final class RedissonTopic {

    private static RedissonClient redissonClient;

    private RedissonTopic() {
    }

    /**
     * 初始化 Redisson 客户端.
     *
     * <p>由 {@link com.frame.me.redis.config.RedissonLockAutoConfiguration} 调用。</p>
     *
     * @param client 默认实例的 Redisson 客户端
     */
    public static void init(RedissonClient client) {
        RedissonTopic.redissonClient = client;
    }

    private static void checkInit() {
        if (redissonClient == null) {
            throw new IllegalStateException("Redisson client is not initialized. Please check the me.redis configuration and redisson dependencies");
        }
    }

    // ============================ Topic ============================

    /**
     * 获取 Topic.
     *
     * @param key 键
     * @return RTopic
     */
    public static RTopic getTopic(String key) {
        checkInit();
        return redissonClient.getTopic(key);
    }

    /**
     * 发布消息.
     *
     * @param key     键
     * @param message 消息
     * @return 接收到的客户端数量
     */
    public static long topicPublish(String key, Object message) {
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
    public static <T> int topicSubscribe(String key, Class<T> type, MessageListener<T> listener) {
        return getTopic(key).addListener(type, listener);
    }

    /**
     * 移除监听器.
     *
     * @param key        键
     * @param listenerId 监听器 ID
     */
    public static void topicUnsubscribe(String key, int listenerId) {
        getTopic(key).removeListener(listenerId);
    }

    // ============================ PatternTopic ============================

    /**
     * 获取模式 Topic.
     *
     * @param pattern 模式
     * @return RPatternTopic
     */
    public static RPatternTopic getPatternTopic(String pattern) {
        checkInit();
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
    public static <T> int patternTopicSubscribe(String pattern, Class<T> type,
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
    public static RReliableTopic getReliableTopic(String key) {
        checkInit();
        return redissonClient.getReliableTopic(key);
    }

    /**
     * 发布可靠消息.
     *
     * @param key     键
     * @param message 消息
     * @return 接收到的客户端数量
     */
    public static long reliableTopicPublish(String key, Object message) {
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
    public static <T> String reliableTopicSubscribe(String key, Class<T> type, MessageListener<T> listener) {
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
    public static RStream<Object, Object> getStream(String key) {
        checkInit();
        return redissonClient.getStream(key);
    }
}
