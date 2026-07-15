package com.frame.me.auth.rbac.redis;

import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.Permission;
import com.frame.me.auth.rbac.redis.config.RbacRedisProperties;
import com.frame.me.auth.rbac.redis.store.PermissionCacheStore;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RedisAuthPermissionProvider} 单元测试，使用内存版缓存存储与计数版数据源.
 *
 * @author frame-me
 */
class RedisAuthPermissionProviderTest {

    /**
     * 内存版缓存存储，替代 Redis，并记录读写次数.
     */
    static class InMemoryStore implements PermissionCacheStore {
        final Map<String, UserPermissionSnapshot> map = new ConcurrentHashMap<>();
        int setCount = 0;

        @Override
        public UserPermissionSnapshot get(String key) {
            return map.get(key);
        }

        @Override
        public void set(String key, UserPermissionSnapshot snapshot, Duration ttl) {
            setCount++;
            map.put(key, snapshot);
        }

        @Override
        public void delete(String key) {
            map.remove(key);
        }
    }

    /**
     * 计数版数据源，验证 read-through 仅在缓存未命中时回源.
     */
    static class CountingSource implements IAuthPermissionProvider {
        int roleCalls = 0;

        @Override
        public Collection<String> getRoles(User user) {
            roleCalls++;
            return Set.of("admin");
        }

        @Override
        public Collection<Permission> getPermissions(User user) {
            return List.of(new Permission("order", "r"));
        }
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    void readThrough_loadsFromSourceThenHitsL1() {
        CountingSource source = new CountingSource();
        InMemoryStore store = new InMemoryStore();
        RedisAuthPermissionProvider provider =
                new RedisAuthPermissionProvider(source, new RbacRedisProperties(), store);

        User user = user(1);
        // 首次读取：L1/L2 均未命中，回源并回填
        assertEquals(Set.of("admin"), provider.getRoles(user));
        assertEquals(1, source.roleCalls);
        assertEquals(1, store.setCount, "回源后应回填 L2");

        // 后续读取命中 L1，不再回源
        assertEquals(1, provider.getPermissions(user).size());
        provider.getRoles(user);
        assertEquals(1, source.roleCalls, "L1 命中不应重复回源");
    }

    @Test
    void l2Hit_doesNotCallSourceOrRewrite() {
        CountingSource source = new CountingSource();
        InMemoryStore store = new InMemoryStore();
        RbacRedisProperties props = new RbacRedisProperties();
        RedisAuthPermissionProvider provider = new RedisAuthPermissionProvider(source, props, store);

        // 预置 L2 快照
        store.map.put(props.getKeyPrefix() + "2",
                new UserPermissionSnapshot(Set.of("saler"), List.of(new Permission("order", "w"))));

        assertEquals(Set.of("saler"), provider.getRoles(user(2)));
        assertEquals(0, source.roleCalls, "L2 命中不应回源");
        assertEquals(0, store.setCount, "L2 命中不应回填");
    }

    @Test
    void evict_clearsL1AndL2ThenReloads() {
        CountingSource source = new CountingSource();
        InMemoryStore store = new InMemoryStore();
        RbacRedisProperties props = new RbacRedisProperties();
        RedisAuthPermissionProvider provider = new RedisAuthPermissionProvider(source, props, store);

        User user = user(3);
        provider.getRoles(user);
        assertEquals(1, source.roleCalls);

        provider.evict(3L);
        assertFalse(store.map.containsKey(props.getKeyPrefix() + "3"), "evict 应删除 L2");

        provider.getRoles(user);
        assertEquals(2, source.roleCalls, "evict 后应重新回源");
    }

    @Test
    void nullUserOrNullId_returnsEmpty() {
        RedisAuthPermissionProvider provider =
                new RedisAuthPermissionProvider(new CountingSource(), new RbacRedisProperties(), new InMemoryStore());
        assertTrue(provider.getRoles(null).isEmpty());
        assertTrue(provider.getPermissions(new User()).isEmpty());
    }
}
