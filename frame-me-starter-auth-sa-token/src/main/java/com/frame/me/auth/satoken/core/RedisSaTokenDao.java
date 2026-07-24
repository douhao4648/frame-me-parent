package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.auto.SaTokenDaoByObjectFollowString;
import cn.dev33.satoken.util.SaFoxUtil;
import com.frame.me.redis.util.RedisClient;
import com.frame.me.redis.util.RedisUtils;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 基于 {@link RedisUtils} 的 Sa-Token 会话存储.
 *
 * <p>实现 {@link SaTokenDaoByObjectFollowString}：Object / SaSession 读写复用 String 通道，
 * 序列化由 {@code SaManager.getSaSerializerTemplate()} 承担（boot4 starter 引入的
 * sa-token-jackson3 提供），与官方 {@code SaTokenDaoForRedisTemplate} 行为完全一致，
 * 因此 <b>不</b>使用 {@link RedisUtils#setObject} 的 fastjson2 序列化路径。</p>
 *
 * <p>timeout 分支语义逐条对齐官方 {@code SaTokenDaoForRedisTemplate}：
 * {@code >0} 限时（秒）、{@code -1(NEVER_EXPIRE)} 永久、{@code 0} 或
 * {@code <= -2(NOT_VALUE_EXPIRE)} 不存储直接返回。</p>
 *
 * <p>所有 key 按 sa-token 传入值原样读写——sa-token 生成的 key 自带 tokenName
 * 前缀（如 {@code satoken:login:token:xxx}），本 DAO 不再叠加命名空间，避免双前缀。
 * {@code clientName} 经 {@link RedisUtils#getClient(String)} 路由到多实例配置。</p>
 *
 * @author frame-me
 */
public class RedisSaTokenDao implements SaTokenDaoByObjectFollowString {

    private final String redisClientName;

    public RedisSaTokenDao(String redisClientName) {
        this.redisClientName = redisClientName;
    }

    /**
     * 按配置解析 Redis 客户端（懒解析，规避与 multi-redis 装配顺序的耦合）.
     *
     * <p>clientName 来自配置，启动后不变，缓存避免每次 HashMap 查找。</p>
     */
    private RedisClient client() {
        return RedisUtils.getClient(redisClientName);
    }

    /**
     * 获取 Value，如无返空.
     */
    @Override
    public String get(String key) {
        return client().get(key);
    }

    /**
     * 写入 Value，并设定存活时间（单位：秒）.
     */
    @Override
    public void set(String key, String value, long timeout) {
        if (timeout == 0 || timeout <= SaTokenDao.NOT_VALUE_EXPIRE) {
            return;
        }
        if (timeout == SaTokenDao.NEVER_EXPIRE) {
            client().set(key, value);
        } else {
            client().set(key, value, timeout, TimeUnit.SECONDS);
        }
    }

    /**
     * 修改指定 key-value 键值对（过期时间不变）.
     */
    @Override
    public void update(String key, String value) {
        Long expireMs = client().obj().getExpire(key, TimeUnit.MILLISECONDS);
        // -2 = 无此键，不再写入
        if (expireMs == null || expireMs == SaTokenDao.NOT_VALUE_EXPIRE) {
            return;
        }
        // -1 = 永不过期，无过期重写
        if (expireMs == SaTokenDao.NEVER_EXPIRE) {
            client().set(key, value);
            return;
        }
        // 0 = key 恰好过期，视作不存在不再写入
        if (expireMs > 0) {
            client().set(key, value, expireMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 删除 Value.
     */
    @Override
    public void delete(String key) {
        client().delete(key);
    }

    /**
     * 获取 Value 的剩余存活时间（单位：秒；-1 永久，-2 不存在）.
     */
    @Override
    public long getTimeout(String key) {
        Long expire = client().getExpire(key);
        return expire == null ? SaTokenDao.NOT_VALUE_EXPIRE : expire;
    }

    /**
     * 修改 Value 的剩余存活时间（单位：秒）.
     */
    @Override
    public void updateTimeout(String key, long timeout) {
        if (timeout == SaTokenDao.NEVER_EXPIRE) {
            // PERSIST：有 TTL 则移除、已永久或 key 不存在则不动，一次往返（官方"整体重写"是三次）
            client().obj().persist(key);
            return;
        }
        client().expire(key, Duration.ofSeconds(timeout));
    }

    /**
     * 搜索数据（sa-token 仅管理端功能使用）.
     *
     * <p>经原生连接的 {@code KEYS} 命令实现，O(N) 复杂度，生产环境慎用；
     * 与官方实现的命令级行为一致（pattern = prefix + "*" + keyword + "*"）。</p>
     */
    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        String pattern = prefix + "*" + keyword + "*";
        RedisConnectionFactory factory = client().obj().getConnectionFactory();
        if (factory == null) {
            return List.of();
        }
        Set<byte[]> rawKeys;
        try (RedisConnection connection = factory.getConnection()) {
            rawKeys = connection.keyCommands().keys(pattern.getBytes(StandardCharsets.UTF_8));
        }
        if (rawKeys == null || rawKeys.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(rawKeys.size());
        for (byte[] rawKey : rawKeys) {
            keys.add(new String(rawKey, StandardCharsets.UTF_8));
        }
        return SaFoxUtil.searchList(keys, start, size, sortType);
    }
}
