package com.frame.me.auth.rbac.redis.store;

import com.alibaba.fastjson2.JSON;
import com.frame.me.auth.rbac.redis.UserPermissionSnapshot;
import com.frame.me.auth.rbac.redis.config.RbacRedisProperties;
import com.frame.me.redis.util.RedisClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * 基于 Redis 的权限快照缓存存储.
 *
 * <p>{@link #get}/{@link #set} 异常时不抛出，仅记录告警并返回 {@code null}/忽略，由上层回退到数据源，
 * 避免 Redis 抖动影响权限校验主链路（可用性降级）。</p>
 *
 * <p>{@link #delete} 与 {@link #bumpVersion} 服务于权限吊销（{@code evict}），属安全动作：异常直接抛给
 * 调用方，让管理端感知「吊销未生效」并重试，而不是静默残留旧快照导致已吊销权限继续生效。</p>
 *
 * <p>快照与随机版本经同一逻辑 key 的摘要映射至同槽 v2 命名空间。
 * 吊销原子删除快照并替换版本，回填仅在版本存在且一致时写入。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class RedisPermissionCacheStore implements IPermissionCacheStore {

    /**
     * 版本缺失或改变时拒绝回填，过期版本不能重新作为版本 0 接受.
     */
    private static final String SET_IF_VERSION_LUA = """
            local current = redis.call('GET', KEYS[2])
            if current ~= false and current == ARGV[1] then
              redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
              return 1
            end
            return 0
            """;
    /**
     * 吊销原子段：删除快照并替换随机版本，不复用已过期的计数器.
     */
    private static final String BUMP_VERSION_LUA = """
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
            return 1
            """;

    private static final RedisScript<String> VERSION_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current ~= false then return current end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return ARGV[1]
            """, String.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final RedisScript<Long> SET_IF_VERSION_SCRIPT =
            new DefaultRedisScript<>(SET_IF_VERSION_LUA, Long.class);
    private static final RedisScript<Long> BUMP_VERSION_SCRIPT =
            new DefaultRedisScript<>(BUMP_VERSION_LUA, Long.class);

    private final RbacRedisProperties properties;
    private final RedisClient redisClient;

    @Override
    public UserPermissionSnapshot get(String key) {
        try {
            return redisClient.getObject(cacheKey(key), UserPermissionSnapshot.class);
        } catch (Exception e) {
            log.warn("读取 Redis 权限缓存失败: key={}", key, e);
            return null;
        }
    }

    @Override
    public void set(String key, UserPermissionSnapshot snapshot, Duration ttl) {
        try {
            redisClient.setObject(cacheKey(key), snapshot, ttl);
        } catch (Exception e) {
            log.warn("写入 Redis 权限缓存失败: key={}", key, e);
        }
    }

    @Override
    public void delete(String key) {
        // 吊销语义：失败必须抛给调用方感知，不做可用性降级
        redisClient.delete(cacheKey(key));
        // 旧命名空间不再读取；删除旧快照，其他旧 key 由原 TTL 回收.
        redisClient.delete(key);
    }

    @Override
    public long version(String key) {
        try {
            String value = redisClient.executeScript(VERSION_SCRIPT, List.of(versionKey(key)),
                    newVersion(), String.valueOf(properties.getRedisTtl().toMillis()));
            return value == null ? -1 : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("读取 Redis 权限版本失败: key={}", key, e);
            return -1;
        }
    }

    @Override
    public boolean setIfVersionMatches(String key, UserPermissionSnapshot snapshot, Duration ttl,
                                       long expectedVersion) {
        try {
            // value 序列化与 setObject 一致（JSON 字符串），Lua 只做版本比对 + SET 原子化
            Long written = redisClient.executeScript(SET_IF_VERSION_SCRIPT, List.of(cacheKey(key), versionKey(key)),
                    String.valueOf(expectedVersion), JSON.toJSONString(snapshot), String.valueOf(ttl.toMillis()));
            if (!Long.valueOf(1L).equals(written)) {
                log.info("权限快照回填被版本围栏拒绝（期间发生吊销）: key={}", key);
                return false;
            }
            return true;
        } catch (Exception e) {
            // 与 set 一致可用性降级：回填失败下次读取回源重建
            log.warn("带版本写入 Redis 权限缓存失败: key={}", key, e);
            return false;
        }
    }

    @Override
    public void bumpVersion(String key) {
        // 吊销语义：失败必须抛给调用方感知，不做可用性降级
        Long result = redisClient.executeScript(BUMP_VERSION_SCRIPT, List.of(cacheKey(key), versionKey(key)),
                newVersion(), String.valueOf(properties.getRedisTtl().toMillis()));
        if (!Long.valueOf(1L).equals(result)) {
            throw new IllegalStateException("Redis 权限版本替换未确认成功");
        }
    }

    private static String versionKey(String key) {
        return cacheKey(key) + ":ver";
    }

    private static String newVersion() {
        return Long.toString(RANDOM.nextLong(Long.MAX_VALUE) + 1);
    }

    private static String cacheKey(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return "me:rbac:v2:{" + HexFormat.of().formatHex(digest) + "}:snapshot";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
