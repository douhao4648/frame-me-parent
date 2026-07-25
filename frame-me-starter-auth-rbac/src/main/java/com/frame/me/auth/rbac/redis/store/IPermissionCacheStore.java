package com.frame.me.auth.rbac.redis.store;

import com.frame.me.auth.rbac.redis.UserPermissionSnapshot;

import java.time.Duration;

/**
 * 权限快照缓存存储 SPI.
 *
 * <p>抽象权限快照的二级（分布式）缓存读写，默认实现 {@link RedisPermissionCacheStore} 基于 Redis。
 * 业务可提供自定义实现接入其他分布式缓存，或用于测试替身。</p>
 *
 * @author frame-me
 */
public interface IPermissionCacheStore {

    /**
     * 读取权限快照.
     *
     * @param key 缓存 key
     * @return 快照，不存在返回 {@code null}
     */
    UserPermissionSnapshot get(String key);

    /**
     * 写入权限快照.
     *
     * @param key      缓存 key
     * @param snapshot 快照
     * @param ttl      过期时间
     */
    void set(String key, UserPermissionSnapshot snapshot, Duration ttl);

    /**
     * 删除权限快照.
     *
     * @param key 缓存 key
     */
    void delete(String key);
}
