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
     * <p>实现允许可用性降级：读取异常时返回 {@code null}，由上层回退到数据源。</p>
     *
     * @param key 缓存 key
     * @return 快照，不存在或读取失败返回 {@code null}
     */
    UserPermissionSnapshot get(String key);

    /**
     * 写入权限快照.
     *
     * <p>实现允许可用性降级：写入异常时静默忽略，上层下次读取会回源重建。</p>
     *
     * @param key      缓存 key
     * @param snapshot 快照
     * @param ttl      过期时间
     */
    void set(String key, UserPermissionSnapshot snapshot, Duration ttl);

    /**
     * 删除权限快照.
     *
     * <p>服务于权限吊销（{@code evict}），属安全动作：实现<b>必须</b>在删除失败时抛出异常，
     * 让调用方感知「吊销未生效」并重试；不允许像读/写一样静默降级，
     * 否则旧快照残留会导致已吊销权限继续生效。</p>
     *
     * @param key 缓存 key
     */
    void delete(String key);
}
