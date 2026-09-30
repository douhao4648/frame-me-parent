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

    /**
     * 读取或原子创建权限版本标识（回源前调用，作为回填围栏）.
     *
     * <p>返回 {@code -1} 表示版本机制不可用（读取失败或实现不支持），
     * 上层直接回源但不回填 L1/L2。</p>
     *
     * @param key 缓存 key
     * @return 版本号，不可用返回 -1
     */
    default long version(String key) {
        return -1;
    }

    /**
     * 仅当当前版本与 {@code expectedVersion} 一致时写入快照（compare-and-set）.
     *
     * <p>防并发回填：实例 A 回源期间实例 B 完成吊销（版本 +1），A 的旧快照写回时
     * 版本不匹配或缺失时拒绝，避免已吊销权限在缓存 TTL 内复活。
     * 默认实现拒绝回填，自定义实现须提供原子版本校验才可启用缓存。</p>
     *
     * @param key             缓存 key
     * @param snapshot        快照
     * @param ttl             过期时间
     * @param expectedVersion 回源前经 {@link #version} 读到的版本
     * @return 是否实际写入（版本不匹配或被拒绝为 false）
     */
    default boolean setIfVersionMatches(String key, UserPermissionSnapshot snapshot, Duration ttl,
                                        long expectedVersion) {
        return false;
    }

    /**
     * 替换权限版本（权限吊销时在 {@link #delete} 后调用），使在途旧快照回填失效.
     *
     * <p>属吊销安全动作的一部分：实现<b>必须</b>像 {@link #delete} 一样在失败时抛出异常。
     * 默认空实现（向后兼容自定义实现）。</p>
     *
     * @param key 缓存 key
     */
    default void bumpVersion(String key) {
    }
}
