package com.frame.me.redis.util;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类.
 *
 * <p>提供常用 KV、Hash、List、Set、ZSet、计数、分布式锁等操作。</p>
 */
@Slf4j
public final class RedisUtils {

    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

    private static StringRedisTemplate stringRedisTemplate;
    private static RedisTemplate<Object, Object> redisTemplate;

    private RedisUtils() {
    }

    /**
     * 初始化工具类模板引用.
     *
     * <p>由 {@link com.frame.me.redis.config.RedisAutoConfiguration} 调用。</p>
     *
     * @param stringRedisTemplate StringRedisTemplate
     * @param redisTemplate       RedisTemplate
     */
    public static void init(StringRedisTemplate stringRedisTemplate, RedisTemplate<Object, Object> redisTemplate) {
        RedisUtils.stringRedisTemplate = stringRedisTemplate;
        RedisUtils.redisTemplate = redisTemplate;
    }

    private static StringRedisTemplate str() {
        if (stringRedisTemplate == null) {
            throw new IllegalStateException("RedisUtils 未初始化，请确认 frame.me.redis.enabled=true 且 RedisAutoConfiguration 已加载");
        }
        return stringRedisTemplate;
    }

    // ============================ String ============================

    /**
     * 设置 String 类型值.
     *
     * @param key   键
     * @param value 值
     */
    public static void set(String key, String value) {
        str().opsForValue().set(key, value);
    }

    /**
     * 设置 String 类型值并指定过期时间.
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时间
     */
    public static void set(String key, String value, Duration timeout) {
        str().opsForValue().set(key, value, timeout);
    }

    /**
     * 设置 String 类型值并指定过期时间.
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时长
     * @param unit    时间单位
     */
    public static void set(String key, String value, long timeout, TimeUnit unit) {
        str().opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 设置对象，序列化为 JSON 字符串存储.
     *
     * @param key     键
     * @param value   对象
     * @param timeout 过期时间
     */
    public static void setObject(String key, Object value, Duration timeout) {
        str().opsForValue().set(key, JSON.toJSONString(value), timeout);
    }

    /**
     * 获取 String 类型值.
     *
     * @param key 键
     * @return 值，不存在返回 null
     */
    public static String get(String key) {
        return str().opsForValue().get(key);
    }

    /**
     * 获取对象并反序列化.
     *
     * @param key   键
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 对象，不存在返回 null
     */
    public static <T> T getObject(String key, Class<T> clazz) {
        String value = get(key);
        if (value == null) {
            return null;
        }
        return JSON.parseObject(value, clazz);
    }

    /**
     * 删除 key.
     *
     * @param key 键
     * @return 是否删除成功
     */
    public static Boolean delete(String key) {
        return str().delete(key);
    }

    /**
     * 批量删除.
     *
     * @param keys 键集合
     * @return 删除数量
     */
    public static Long delete(Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return 0L;
        }
        return str().delete(keys);
    }

    /**
     * 设置过期时间.
     *
     * @param key     键
     * @param timeout 过期时间
     * @return 是否设置成功
     */
    public static Boolean expire(String key, Duration timeout) {
        return str().expire(key, timeout);
    }

    /**
     * 获取过期时间.
     *
     * @param key 键
     * @return 剩余秒数，-1 表示永不过期，-2 表示不存在
     */
    public static Long getExpire(String key) {
        return str().getExpire(key);
    }

    /**
     * 判断 key 是否存在.
     *
     * @param key 键
     * @return 是否存在
     */
    public static Boolean hasKey(String key) {
        return str().hasKey(key);
    }

    // ============================ Hash ============================

    /**
     * 设置 Hash 字段.
     *
     * @param key     键
     * @param hashKey Hash 键
     * @param value   值
     */
    public static void hSet(String key, String hashKey, Object value) {
        str().opsForHash().put(key, hashKey, JSON.toJSONString(value));
    }

    /**
     * 批量设置 Hash.
     *
     * @param key 键
     * @param map 映射
     */
    public static void hSetAll(String key, Map<String, Object> map) {
        str().opsForHash().putAll(key, map);
    }

    /**
     * 获取 Hash 字段.
     *
     * @param key     键
     * @param hashKey Hash 键
     * @return 值
     */
    public static String hGet(String key, String hashKey) {
        Object value = str().opsForHash().get(key, hashKey);
        return Objects.toString(value, null);
    }

    /**
     * 获取 Hash 字段并反序列化.
     *
     * @param key     键
     * @param hashKey Hash 键
     * @param clazz   目标类型
     * @param <T>     类型参数
     * @return 对象
     */
    public static <T> T hGet(String key, String hashKey, Class<T> clazz) {
        String value = hGet(key, hashKey);
        if (value == null) {
            return null;
        }
        return JSON.parseObject(value, clazz);
    }

    /**
     * 获取整个 Hash.
     *
     * @param key 键
     * @return Hash 映射
     */
    public static Map<Object, Object> hGetAll(String key) {
        return str().opsForHash().entries(key);
    }

    /**
     * 删除 Hash 字段.
     *
     * @param key      键
     * @param hashKeys Hash 键数组
     * @return 删除数量
     */
    public static Long hDelete(String key, Object... hashKeys) {
        return str().opsForHash().delete(key, hashKeys);
    }

    /**
     * 判断 Hash 字段是否存在.
     *
     * @param key     键
     * @param hashKey Hash 键
     * @return 是否存在
     */
    public static Boolean hHasKey(String key, String hashKey) {
        return str().opsForHash().hasKey(key, hashKey);
    }

    // ============================ List ============================

    /**
     * 左入队.
     *
     * @param key   键
     * @param value 值
     * @return 列表长度
     */
    public static Long lPush(String key, String value) {
        return str().opsForList().leftPush(key, value);
    }

    /**
     * 右入队.
     *
     * @param key   键
     * @param value 值
     * @return 列表长度
     */
    public static Long rPush(String key, String value) {
        return str().opsForList().rightPush(key, value);
    }

    /**
     * 左出队.
     *
     * @param key 键
     * @return 值
     */
    public static String lPop(String key) {
        return str().opsForList().leftPop(key);
    }

    /**
     * 右出队.
     *
     * @param key 键
     * @return 值
     */
    public static String rPop(String key) {
        return str().opsForList().rightPop(key);
    }

    /**
     * 获取列表范围.
     *
     * @param key   键
     * @param start 开始索引
     * @param end   结束索引
     * @return 值列表
     */
    public static List<String> lRange(String key, long start, long end) {
        return str().opsForList().range(key, start, end);
    }

    /**
     * 获取列表长度.
     *
     * @param key 键
     * @return 长度
     */
    public static Long lSize(String key) {
        return str().opsForList().size(key);
    }

    // ============================ Set ============================

    /**
     * 添加 Set 成员.
     *
     * @param key    键
     * @param values 成员
     * @return 新增数量
     */
    public static Long sAdd(String key, String... values) {
        return str().opsForSet().add(key, values);
    }

    /**
     * 获取 Set 所有成员.
     *
     * @param key 键
     * @return 成员集合
     */
    public static Set<String> sMembers(String key) {
        return str().opsForSet().members(key);
    }

    /**
     * 判断是否是 Set 成员.
     *
     * @param key    键
     * @param member 成员
     * @return 是否成员
     */
    public static Boolean sIsMember(String key, String member) {
        return str().opsForSet().isMember(key, member);
    }

    /**
     * 删除 Set 成员.
     *
     * @param key    键
     * @param values 成员
     * @return 删除数量
     */
    public static Long sRemove(String key, Object... values) {
        return str().opsForSet().remove(key, values);
    }

    // ============================ ZSet ============================

    /**
     * 添加 ZSet 成员.
     *
     * @param key   键
     * @param value 成员
     * @param score 分数
     * @return 是否新增
     */
    public static Boolean zAdd(String key, String value, double score) {
        return str().opsForZSet().add(key, value, score);
    }

    /**
     * 按分数范围获取 ZSet 成员.
     *
     * @param key   键
     * @param start 开始分数
     * @param end   结束分数
     * @return 成员集合
     */
    public static Set<String> zRangeByScore(String key, double start, double end) {
        return str().opsForZSet().rangeByScore(key, start, end);
    }

    /**
     * 删除 ZSet 成员.
     *
     * @param key    键
     * @param values 成员
     * @return 删除数量
     */
    public static Long zRemove(String key, Object... values) {
        return str().opsForZSet().remove(key, values);
    }

    // ============================ Counter ============================

    /**
     * 自增.
     *
     * @param key   键
     * @param delta 增量
     * @return 增加后的值
     */
    public static Long increment(String key, long delta) {
        return str().opsForValue().increment(key, delta);
    }

    /**
     * 自减.
     *
     * @param key   键
     * @param delta 减量
     * @return 减少后的值
     */
    public static Long decrement(String key, long delta) {
        return str().opsForValue().decrement(key, delta);
    }

    // ============================ Lock ============================

    /**
     * 尝试获取分布式锁.
     *
     * <p>基于 {@code SET key value NX PX milliseconds} 实现，仅提供最简单的互斥语义。
     * 若需要可重入、看门狗续期，请使用 Redisson。</p>
     *
     * @param key        锁键
     * @param value      锁标识（建议用 UUID）
     * @param expireMs   锁过期时间（毫秒）
     * @return 是否获取成功
     */
    public static Boolean tryLock(String key, String value, long expireMs) {
        Boolean result = str().opsForValue().setIfAbsent(key, value, expireMs, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 释放分布式锁.
     *
     * <p>使用 Lua 脚本保证"判断标识 + 删除"的原子性。</p>
     *
     * @param key   锁键
     * @param value 锁标识
     * @return 是否释放成功
     */
    public static Boolean unlock(String key, String value) {
        Long result = str().execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
        return result != null && result > 0;
    }

}
