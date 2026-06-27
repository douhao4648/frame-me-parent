package com.frame.me.redis.util;

import com.alibaba.fastjson2.JSON;
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
 * Redis 客户端操作类.
 *
 * <p>封装单个 Redis 实例的常用操作，可通过 {@link RedisUtils#getClient(String)} 获取指定实例。
 * 这里的 {@link #tryLock} 是基于 {@code SET NX PX} 的简单锁；若引入 Redisson，可改用 {@link RedissonLock}
 * 获得可重入与看门狗续期。</p>
 */
public class RedisClient {

    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<Object, Object> redisTemplate;

    RedisClient(StringRedisTemplate stringRedisTemplate, RedisTemplate<Object, Object> redisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
    }

    private StringRedisTemplate str() {
        if (stringRedisTemplate == null) {
            throw new IllegalStateException("RedisClient is not initialized");
        }
        return stringRedisTemplate;
    }

    /**
     * 获取原生 Object RedisTemplate（可直接存取任意对象）.
     *
     * @return RedisTemplate
     */
    public RedisTemplate<Object, Object> obj() {
        if (redisTemplate == null) {
            throw new IllegalStateException("RedisClient is not initialized");
        }
        return redisTemplate;
    }

    // ============================ String ============================

    /**
     * 设置 String 类型值.
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, String value) {
        str().opsForValue().set(key, value);
    }

    /**
     * 设置 String 类型值并指定过期时间.
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时间
     */
    public void set(String key, String value, Duration timeout) {
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
    public void set(String key, String value, long timeout, TimeUnit unit) {
        str().opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 设置对象，序列化为 JSON 字符串存储.
     *
     * @param key     键
     * @param value   对象
     * @param timeout 过期时间
     */
    public void setObject(String key, Object value, Duration timeout) {
        str().opsForValue().set(key, JSON.toJSONString(value), timeout);
    }

    /**
     * 获取 String 类型值.
     *
     * @param key 键
     * @return 值，不存在返回 null
     */
    public String get(String key) {
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
    public <T> T getObject(String key, Class<T> clazz) {
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
    public Boolean delete(String key) {
        return str().delete(key);
    }

    /**
     * 批量删除.
     *
     * @param keys 键集合
     * @return 删除数量
     */
    public Long delete(Collection<String> keys) {
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
    public Boolean expire(String key, Duration timeout) {
        return str().expire(key, timeout);
    }

    /**
     * 获取过期时间.
     *
     * @param key 键
     * @return 剩余秒数，-1 表示永不过期，-2 表示不存在
     */
    public Long getExpire(String key) {
        return str().getExpire(key);
    }

    /**
     * 判断 key 是否存在.
     *
     * @param key 键
     * @return 是否存在
     */
    public Boolean hasKey(String key) {
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
    public void hSet(String key, String hashKey, Object value) {
        str().opsForHash().put(key, hashKey, JSON.toJSONString(value));
    }

    /**
     * 批量设置 Hash.
     *
     * @param key 键
     * @param map 映射
     */
    public void hSetAll(String key, Map<String, Object> map) {
        str().opsForHash().putAll(key, map);
    }

    /**
     * 获取 Hash 字段.
     *
     * @param key     键
     * @param hashKey Hash 键
     * @return 值
     */
    public String hGet(String key, String hashKey) {
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
    public <T> T hGet(String key, String hashKey, Class<T> clazz) {
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
    public Map<Object, Object> hGetAll(String key) {
        return str().opsForHash().entries(key);
    }

    /**
     * 删除 Hash 字段.
     *
     * @param key      键
     * @param hashKeys Hash 键数组
     * @return 删除数量
     */
    public Long hDelete(String key, Object... hashKeys) {
        return str().opsForHash().delete(key, hashKeys);
    }

    /**
     * 判断 Hash 字段是否存在.
     *
     * @param key     键
     * @param hashKey Hash 键
     * @return 是否存在
     */
    public Boolean hHasKey(String key, String hashKey) {
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
    public Long lPush(String key, String value) {
        return str().opsForList().leftPush(key, value);
    }

    /**
     * 右入队.
     *
     * @param key   键
     * @param value 值
     * @return 列表长度
     */
    public Long rPush(String key, String value) {
        return str().opsForList().rightPush(key, value);
    }

    /**
     * 左出队.
     *
     * @param key 键
     * @return 值
     */
    public String lPop(String key) {
        return str().opsForList().leftPop(key);
    }

    /**
     * 右出队.
     *
     * @param key 键
     * @return 值
     */
    public String rPop(String key) {
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
    public List<String> lRange(String key, long start, long end) {
        return str().opsForList().range(key, start, end);
    }

    /**
     * 获取列表长度.
     *
     * @param key 键
     * @return 长度
     */
    public Long lSize(String key) {
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
    public Long sAdd(String key, String... values) {
        return str().opsForSet().add(key, values);
    }

    /**
     * 获取 Set 所有成员.
     *
     * @param key 键
     * @return 成员集合
     */
    public Set<String> sMembers(String key) {
        return str().opsForSet().members(key);
    }

    /**
     * 判断是否是 Set 成员.
     *
     * @param key    键
     * @param member 成员
     * @return 是否成员
     */
    public Boolean sIsMember(String key, String member) {
        return str().opsForSet().isMember(key, member);
    }

    /**
     * 删除 Set 成员.
     *
     * @param key    键
     * @param values 成员
     * @return 删除数量
     */
    public Long sRemove(String key, Object... values) {
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
    public Boolean zAdd(String key, String value, double score) {
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
    public Set<String> zRangeByScore(String key, double start, double end) {
        return str().opsForZSet().rangeByScore(key, start, end);
    }

    /**
     * 删除 ZSet 成员.
     *
     * @param key    键
     * @param values 成员
     * @return 删除数量
     */
    public Long zRemove(String key, Object... values) {
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
    public Long increment(String key, long delta) {
        return str().opsForValue().increment(key, delta);
    }

    /**
     * 自减.
     *
     * @param key   键
     * @param delta 减量
     * @return 减少后的值
     */
    public Long decrement(String key, long delta) {
        return str().opsForValue().decrement(key, delta);
    }

    // ============================ Lock ============================

    /**
     * 尝试获取分布式锁.
     *
     * <p>基于 {@code SET key value NX PX milliseconds} 实现，仅提供最简单的互斥语义。
     * 若需要可重入、看门狗续期，请引入 Redisson 并使用 {@link RedissonLock}。</p>
     *
     * @param key      锁键
     * @param value    锁标识（建议用 UUID）
     * @param expireMs 锁过期时间（毫秒）
     * @return 是否获取成功
     */
    public Boolean tryLock(String key, String value, long expireMs) {
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
    public Boolean unlock(String key, String value) {
        Long result = str().execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
        return result != null && result > 0;
    }

}
