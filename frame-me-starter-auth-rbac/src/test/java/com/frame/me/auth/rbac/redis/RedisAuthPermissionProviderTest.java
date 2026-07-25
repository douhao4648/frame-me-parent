package com.frame.me.auth.rbac.redis;

import com.alibaba.fastjson2.JSON;
import com.frame.me.auth.rbac.permission.DataPermission;
import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.Permission;
import com.frame.me.auth.rbac.redis.config.RbacRedisProperties;
import com.frame.me.auth.rbac.redis.store.IPermissionCacheStore;
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
    static class InMemoryStore implements IPermissionCacheStore {
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
        int dataPermCalls = 0;

        @Override
        public Collection<String> getRoles(User user) {
            roleCalls++;
            return Set.of("admin");
        }

        @Override
        public Collection<Permission> getPermissions(User user) {
            return List.of(new Permission("order", "r"));
        }

        @Override
        public Collection<DataPermission> getDataPermissions(User user) {
            dataPermCalls++;
            return List.of(new DataPermission("order", "*", "CUSTOM", Set.of(5L, 7L)));
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

        // 预置 L2 快照（旧格式 JSON 反序列化场景：dataPermissions 为空列表）
        store.map.put(props.getKeyPrefix() + "2",
                new UserPermissionSnapshot(Set.of("saler"), List.of(new Permission("order", "w")), List.of()));

        assertEquals(Set.of("saler"), provider.getRoles(user(2)));
        assertEquals(0, source.roleCalls, "L2 命中不应回源");
        assertEquals(0, store.setCount, "L2 命中不应回填");
        assertTrue(provider.getDataPermissions(user(2)).isEmpty(), "L2 旧格式快照数据权限应为空");
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
        assertTrue(provider.getDataPermissions(null).isEmpty());
        assertTrue(provider.getDataPermissions(new User()).isEmpty());
    }

    @Test
    void readThrough_includesDataPermissions() {
        CountingSource source = new CountingSource();
        InMemoryStore store = new InMemoryStore();
        RedisAuthPermissionProvider provider =
                new RedisAuthPermissionProvider(source, new RbacRedisProperties(), store);

        User user = user(4);
        // 首次读取数据权限：回源并回填
        Collection<DataPermission> dataPermissions = provider.getDataPermissions(user);
        assertEquals(1, dataPermissions.size());
        DataPermission dp = dataPermissions.iterator().next();
        assertEquals("CUSTOM", dp.getDataScope());
        assertEquals(Set.of(5L, 7L), dp.getDataIds());
        assertEquals(1, source.dataPermCalls, "首次读取应回源");
        assertEquals(1, store.setCount, "回源后应回填 L2（含数据权限）");

        // 后续读取命中 L1，不再回源
        provider.getDataPermissions(user);
        assertEquals(1, source.dataPermCalls, "L1 命中不应重复回源");
    }

    @Test
    void oldSnapshotJson_missingDataPermissionsField_deserializesToEmptyList() {
        // 模拟升级前写入 Redis 的旧格式 JSON（无 dataPermissions 字段）
        String oldJson = "{\"roles\":[\"admin\"],\"permissions\":[{\"resource\":\"order\",\"action\":\"r\"}]}";

        UserPermissionSnapshot snapshot = JSON.parseObject(oldJson, UserPermissionSnapshot.class);

        assertEquals(Set.of("admin"), snapshot.getRoles());
        assertEquals(1, snapshot.getPermissions().size());
        assertTrue(snapshot.getDataPermissions().isEmpty(),
                "旧格式 JSON 缺少 dataPermissions 字段时应保留字段初始空列表，不得为 null");
    }
}
