package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.auto.SaTokenDaoByObjectFollowString;
import cn.dev33.satoken.util.SaFoxUtil;
import com.frame.me.redis.util.RedisClient;
import com.frame.me.redis.util.RedisUtils;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    /**
     * 原子更新脚本：读 PTTL → 按 TTL 原值重写 value，消除 getExpire+set 的 TOCTOU 竞态.
     *
     * <p>返回值：0=key 不存在跳过；1=已重写（永久或按原 TTL）.</p>
     */
    private static final String UPDATE_LUA = """
            local ttl = redis.call('pttl', KEYS[1])
            if ttl == -2 then return 0 end
            if ttl == -1 then redis.call('set', KEYS[1], ARGV[1]) return 1 end
            if ttl > 0 then redis.call('set', KEYS[1], ARGV[1], 'px', ttl) return 1 end
            return 0
            """;
    private static final RedisScript<Long> UPDATE_SCRIPT = new DefaultRedisScript<>(UPDATE_LUA, Long.class);
    /**
     * SCAN 游标迭代上限，防止 keyspace 过大时无限扫描拖垮管理端调用.
     */
    private static final int SCAN_LIMIT = 10_000;
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
     *
     * <p>用 Lua 脚本原子完成「读 PTTL → 按原 TTL 重写 value」，消除旧实现
     * {@code getExpire} + {@code set} 两步之间的 TOCTOU 竞态（TTL 被其他线程续期/缩短后覆盖）.
     * 必须走 {@link RedisClient#executeScript}（String 通道，与 {@code set} 同一序列化），
     * 走 {@code obj()} 的 JDK 序列化会让脚本读到另一个 key（pttl 恒 -2，静默不写）.</p>
     */
    @Override
    public void update(String key, String value) {
        client().executeScript(UPDATE_SCRIPT, Collections.singletonList(key), value);
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
            // PERSIST：有 TTL 则移除、已永久或 key 不存在则不动，一次往返（官方"整体重写"是三次）。
            // 走 String 通道（同 set），obj() 的 JDK 序列化会 PERSIST 到另一个不存在的 key
            client().persist(key);
            return;
        }
        client().expire(key, Duration.ofSeconds(timeout));
    }

    /**
     * 搜索数据（sa-token 仅管理端功能使用）.
     *
     * <p>经原生连接的 {@code SCAN} 命令游标式迭代实现，O(N) 但非阻塞、不卡 Redis 主线程，
     * 生产环境安全；替代旧的 {@code KEYS}（会遍历整个 keyspace 阻塞主线程）。
     * pattern 组成与官方一致：{@code prefix + "*" + keyword + "*"}.</p>
     * <p>游标上限 {@code SCAN_LIMIT} 防止 keyspace 过大时无限扫描拖垮管理端调用.</p>
     */
    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        String pattern = prefix + "*" + keyword + "*";
        RedisConnectionFactory factory = client().obj().getConnectionFactory();
        if (factory == null) {
            return List.of();
        }
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();
        List<String> keys = new ArrayList<>();
        try (RedisConnection connection = factory.getConnection()) {
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext() && keys.size() < SCAN_LIMIT) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            // SCAN 失败（如 Redis 不可达）返回空，管理端功能降级而非抛异常
            return List.of();
        }
        if (keys.isEmpty()) {
            return List.of();
        }
        return SaFoxUtil.searchList(keys, start, size, sortType);
    }
}
