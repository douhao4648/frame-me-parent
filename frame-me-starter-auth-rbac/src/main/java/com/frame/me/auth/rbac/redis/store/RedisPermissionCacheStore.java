package com.frame.me.auth.rbac.redis.store;

import com.frame.me.auth.rbac.redis.UserPermissionSnapshot;
import com.frame.me.auth.rbac.redis.config.RbacRedisProperties;
import com.frame.me.redis.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * 基于 Redis 的权限快照缓存存储.
 *
 * <p>读写异常时不抛出，仅记录告警并返回 {@code null}/忽略，由上层回退到数据源，
 * 避免 Redis 抖动影响权限校验主链路。</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class RedisPermissionCacheStore implements PermissionCacheStore {

    private final RbacRedisProperties properties;

    @Override
    public UserPermissionSnapshot get(String key) {
        try {
            return RedisUtils.getClient(properties.getClientName()).getObject(key, UserPermissionSnapshot.class);
        } catch (Exception e) {
            log.warn("读取 Redis 权限缓存失败: key={}", key, e);
            return null;
        }
    }

    @Override
    public void set(String key, UserPermissionSnapshot snapshot, Duration ttl) {
        try {
            RedisUtils.getClient(properties.getClientName()).setObject(key, snapshot, ttl);
        } catch (Exception e) {
            log.warn("写入 Redis 权限缓存失败: key={}", key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            RedisUtils.getClient(properties.getClientName()).delete(key);
        } catch (Exception e) {
            log.warn("删除 Redis 权限缓存失败: key={}", key, e);
        }
    }
}
