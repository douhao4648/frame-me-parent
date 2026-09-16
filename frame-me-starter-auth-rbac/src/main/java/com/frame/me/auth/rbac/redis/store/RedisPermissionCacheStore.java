package com.frame.me.auth.rbac.redis.store;

import com.frame.me.auth.rbac.redis.UserPermissionSnapshot;
import com.frame.me.auth.rbac.redis.config.RbacRedisProperties;
import com.frame.me.redis.util.RedisClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * 基于 Redis 的权限快照缓存存储.
 *
 * <p>{@link #get}/{@link #set} 异常时不抛出，仅记录告警并返回 {@code null}/忽略，由上层回退到数据源，
 * 避免 Redis 抖动影响权限校验主链路（可用性降级）。</p>
 *
 * <p>{@link #delete} 服务于权限吊销（{@code evict}），属安全动作：异常直接抛给调用方，
 * 让管理端感知「吊销未生效」并重试，而不是静默残留旧快照导致已吊销权限继续生效。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class RedisPermissionCacheStore implements IPermissionCacheStore {

    private final RbacRedisProperties properties;
    private final RedisClient redisClient;

    @Override
    public UserPermissionSnapshot get(String key) {
        try {
            return redisClient.getObject(key, UserPermissionSnapshot.class);
        } catch (Exception e) {
            log.warn("读取 Redis 权限缓存失败: key={}", key, e);
            return null;
        }
    }

    @Override
    public void set(String key, UserPermissionSnapshot snapshot, Duration ttl) {
        try {
            redisClient.setObject(key, snapshot, ttl);
        } catch (Exception e) {
            log.warn("写入 Redis 权限缓存失败: key={}", key, e);
        }
    }

    @Override
    public void delete(String key) {
        // 吊销语义：失败必须抛给调用方感知，不做可用性降级
        redisClient.delete(key);
    }
}
