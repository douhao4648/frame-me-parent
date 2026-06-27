package com.frame.me.redis.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 工具类入口.
 *
 * <p>提供默认实例的快捷静态方法，以及通过 {@link #getClient(String)} 获取指定实例的 {@link RedisClient}。</p>
 */
public final class RedisUtils {

    private static final Map<String, RedisClient> CLIENT_MAP = new ConcurrentHashMap<>();
    private static String defaultClientName = "default";

    private RedisUtils() {
    }

    /**
     * 初始化工具类客户端映射.
     *
     * <p>由 {@link com.frame.me.redis.config.RedisAutoConfiguration} 调用。</p>
     *
     * @param defaultClientName 默认实例名称
     * @param stringTemplates   所有 StringRedisTemplate 实例
     * @param templates         所有 RedisTemplate 实例
     */
    public static void init(String defaultClientName,
                            Map<String, StringRedisTemplate> stringTemplates,
                            Map<String, RedisTemplate<Object, Object>> templates) {
        RedisUtils.defaultClientName = defaultClientName;
        CLIENT_MAP.clear();
        stringTemplates.forEach((name, stringTemplate) -> {
            RedisTemplate<Object, Object> template = templates.get(name);
            CLIENT_MAP.put(name, new RedisClient(stringTemplate, template));
        });
    }

    /**
     * 获取默认 Redis 客户端.
     *
     * @return 默认 RedisClient
     */
    public static RedisClient getClient() {
        return getClient(defaultClientName);
    }

    /**
     * 获取指定名称的 Redis 客户端.
     *
     * @param name 实例名称
     * @return 对应 RedisClient
     */
    public static RedisClient getClient(String name) {
        RedisClient client = CLIENT_MAP.get(name);
        if (client == null) {
            throw new IllegalStateException("Redis client '" + name + "' Not registered. Please check the frame.me.redis.clients configuration");
        }
        return client;
    }

    private static RedisClient client() {
        return getClient();
    }

    // ============================ 默认实例委托 ============================

    public static void set(String key, String value) {
        client().set(key, value);
    }

    public static void set(String key, String value, java.time.Duration timeout) {
        client().set(key, value, timeout);
    }

    public static void set(String key, String value, long timeout, java.util.concurrent.TimeUnit unit) {
        client().set(key, value, timeout, unit);
    }

    public static void setObject(String key, Object value, java.time.Duration timeout) {
        client().setObject(key, value, timeout);
    }

    public static String get(String key) {
        return client().get(key);
    }

    public static <T> T getObject(String key, Class<T> clazz) {
        return client().getObject(key, clazz);
    }

    public static Boolean delete(String key) {
        return client().delete(key);
    }

    public static Long delete(java.util.Collection<String> keys) {
        return client().delete(keys);
    }

    public static Boolean expire(String key, java.time.Duration timeout) {
        return client().expire(key, timeout);
    }

    public static Long getExpire(String key) {
        return client().getExpire(key);
    }

    public static Boolean hasKey(String key) {
        return client().hasKey(key);
    }

    public static void hSet(String key, String hashKey, Object value) {
        client().hSet(key, hashKey, value);
    }

    public static void hSetAll(String key, java.util.Map<String, Object> map) {
        client().hSetAll(key, map);
    }

    public static String hGet(String key, String hashKey) {
        return client().hGet(key, hashKey);
    }

    public static <T> T hGet(String key, String hashKey, Class<T> clazz) {
        return client().hGet(key, hashKey, clazz);
    }

    public static java.util.Map<Object, Object> hGetAll(String key) {
        return client().hGetAll(key);
    }

    public static Long hDelete(String key, Object... hashKeys) {
        return client().hDelete(key, hashKeys);
    }

    public static Boolean hHasKey(String key, String hashKey) {
        return client().hHasKey(key, hashKey);
    }

    public static Long lPush(String key, String value) {
        return client().lPush(key, value);
    }

    public static Long rPush(String key, String value) {
        return client().rPush(key, value);
    }

    public static String lPop(String key) {
        return client().lPop(key);
    }

    public static String rPop(String key) {
        return client().rPop(key);
    }

    public static java.util.List<String> lRange(String key, long start, long end) {
        return client().lRange(key, start, end);
    }

    public static Long lSize(String key) {
        return client().lSize(key);
    }

    public static Long sAdd(String key, String... values) {
        return client().sAdd(key, values);
    }

    public static java.util.Set<String> sMembers(String key) {
        return client().sMembers(key);
    }

    public static Boolean sIsMember(String key, String member) {
        return client().sIsMember(key, member);
    }

    public static Long sRemove(String key, Object... values) {
        return client().sRemove(key, values);
    }

    public static Boolean zAdd(String key, String value, double score) {
        return client().zAdd(key, value, score);
    }

    public static java.util.Set<String> zRangeByScore(String key, double start, double end) {
        return client().zRangeByScore(key, start, end);
    }

    public static Long zRemove(String key, Object... values) {
        return client().zRemove(key, values);
    }

    public static Long increment(String key, long delta) {
        return client().increment(key, delta);
    }

    public static Long decrement(String key, long delta) {
        return client().decrement(key, delta);
    }

    public static Boolean tryLock(String key, String value, long expireMs) {
        return client().tryLock(key, value, expireMs);
    }

    public static Boolean unlock(String key, String value) {
        return client().unlock(key, value);
    }
}
