package com.frame.me.auth.rbac.redis;

import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.Permission;
import com.frame.me.auth.rbac.redis.config.RbacRedisProperties;
import com.frame.me.auth.rbac.redis.store.PermissionCacheStore;
import com.frame.me.base.user.User;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * 基于 Redis 的权限提供者（read-through 缓存）.
 *
 * <p>读取链路：L1 本地缓存（Caffeine，短 TTL）→ L2 Redis（{@link PermissionCacheStore}）→ 委托数据源。
 * 回源后回填 Redis 与本地缓存。当所有服务共享同一 Redis 时，RBAC 判定天然一致，且支持权限新鲜与吊销
 * （权限变更后调用 {@link #evict(Object)} 失效缓存）。</p>
 *
 * @author frame-me
 */
@Slf4j
public class RedisAuthPermissionProvider implements IAuthPermissionProvider {

    /**
     * 委托的真实数据源（如配置或数据库实现）.
     */
    private final IAuthPermissionProvider source;

    private final RbacRedisProperties properties;

    private final PermissionCacheStore cacheStore;

    /**
     * L1 本地缓存，降低 Redis 读压力.
     */
    private final Cache<String, UserPermissionSnapshot> localCache;

    public RedisAuthPermissionProvider(IAuthPermissionProvider source,
                                       RbacRedisProperties properties,
                                       PermissionCacheStore cacheStore) {
        this.source = source;
        this.properties = properties;
        this.cacheStore = cacheStore;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(properties.getLocalMaxSize())
                .expireAfterWrite(properties.getLocalTtl())
                .build();
    }

    @Override
    public Collection<String> getRoles(User user) {
        return load(user).getRoles();
    }

    @Override
    public Collection<Permission> getPermissions(User user) {
        return load(user).getPermissions();
    }

    /**
     * 失效指定用户的权限缓存（L1 + L2），权限变更时调用.
     *
     * @param userId 用户 ID
     */
    public void evict(Object userId) {
        if (userId == null) {
            return;
        }
        String key = key(userId);
        localCache.invalidate(key);
        cacheStore.delete(key);
    }

    private UserPermissionSnapshot load(User user) {
        if (user == null || user.getId() == null) {
            return new UserPermissionSnapshot();
        }
        String key = key(user.getId());

        UserPermissionSnapshot snapshot = localCache.getIfPresent(key);
        if (snapshot != null) {
            return snapshot;
        }

        snapshot = cacheStore.get(key);
        if (snapshot == null) {
            snapshot = loadFromSource(user);
            cacheStore.set(key, snapshot, properties.getRedisTtl());
        }
        localCache.put(key, snapshot);
        return snapshot;
    }

    private UserPermissionSnapshot loadFromSource(User user) {
        UserPermissionSnapshot snapshot = new UserPermissionSnapshot();
        snapshot.setRoles(new HashSet<>(source.getRoles(user)));
        snapshot.setPermissions(new ArrayList<>(source.getPermissions(user)));
        return snapshot;
    }

    private String key(Object userId) {
        return properties.getKeyPrefix() + userId;
    }
}
