package com.frame.me.auth.rbac.redis.store;

import com.frame.me.auth.rbac.permission.IAuthPermissionProvider;
import com.frame.me.auth.rbac.permission.Permission;
import com.frame.me.auth.rbac.redis.RedisAuthPermissionProvider;
import com.frame.me.auth.rbac.redis.UserPermissionSnapshot;
import com.frame.me.auth.rbac.redis.config.RbacRedisProperties;
import com.frame.me.base.user.User;
import com.frame.me.redis.util.RedisClient;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class RedisPermissionCacheStoreIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--cluster-enabled", "yes", "--cluster-config-file", "nodes.conf");

    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;
    private RedisClient client;
    private RbacRedisProperties properties;
    private RedisPermissionCacheStore store;
    private String key;

    @BeforeAll
    static void assignSlots() throws Exception {
        assertEquals("OK", REDIS.execInContainer("redis-cli", "CLUSTER", "ADDSLOTSRANGE", "0", "16383")
                .getStdout().trim());
        await().atMost(Duration.ofSeconds(10)).until(() -> REDIS.execInContainer("redis-cli", "CLUSTER", "INFO")
                .getStdout().contains("cluster_state:ok"));
    }

    @BeforeEach
    void setup() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        client = new RedisClient(template, null);
        properties = new RbacRedisProperties();
        store = new RedisPermissionCacheStore(properties, client);
        key = "auth:perms:" + UUID.randomUUID();
    }

    @AfterEach
    void close() {
        factory.destroy();
    }

    @Test
    void clusterRoundTripAndRevocationRejectStaleBackfill() {
        long version = store.version(key);
        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(Set.of("admin"), List.of(), List.of());
        assertTrue(store.setIfVersionMatches(key, snapshot, Duration.ofMinutes(1), version));
        assertEquals(Set.of("admin"), store.get(key).getRoles());
        store.bumpVersion(key);
        assertNull(store.get(key));
        assertFalse(store.setIfVersionMatches(key, snapshot, Duration.ofMinutes(1), version));
        assertNull(store.get(key));
    }

    @Test
    void expiredAndRecreatedGenerationRejectOldBackfill() {
        properties.setRedisTtl(Duration.ofMillis(100));
        long old = store.version(key);
        await().atMost(Duration.ofSeconds(5)).until(() -> Boolean.FALSE.equals(template.hasKey(versionKey())));
        assertFalse(store.setIfVersionMatches(key, new UserPermissionSnapshot(), Duration.ofMinutes(1), old));
        long replacement = store.version(key);
        assertNotEquals(old, replacement);
        assertFalse(store.setIfVersionMatches(key, new UserPermissionSnapshot(), Duration.ofMinutes(1), old));
    }

    @Test
    void legacySnapshotIsNotReadAndLogicalNamespacesRemainIsolated() {
        client.setObject(key, new UserPermissionSnapshot(Set.of("legacy"), List.of(), List.of()), Duration.ofMinutes(1));
        assertNull(store.get(key));
        String other = "other:{existing-tag}:" + key;
        assertTrue(store.setIfVersionMatches(other,
                new UserPermissionSnapshot(Set.of("other"), List.of(), List.of()), Duration.ofMinutes(1), store.version(other)));
        assertNull(store.get(key));
        assertEquals(Set.of("other"), store.get(other).getRoles());
        store.delete(key);
        assertNull(client.get(key));
    }

    @Test
    void versionReadFailureThenRecoveryDoesNotResurrectRevokedPermissions() throws Exception {
        concurrentRevocation(true);
    }

    @Test
    void rejectedBackfillDoesNotPopulateLocalCache() throws Exception {
        concurrentRevocation(false);
    }

    private void concurrentRevocation(boolean failVersionRead) throws Exception {
        AtomicBoolean failOnce = new AtomicBoolean(true);
        RedisClient transientFailure = new RedisClient(template, null) {
            @Override
            public <T> T executeScript(RedisScript<T> script, List<String> keys, Object... args) {
                if (failVersionRead && script.getResultType() == String.class && failOnce.getAndSet(false)) {
                    throw new IllegalStateException("transient read failure");
                }
                return super.executeScript(script, keys, args);
            }
        };
        properties.setKeyPrefix(key + ":");
        AtomicReference<Set<String>> roles = new AtomicReference<>(Set.of("admin"));
        CountDownLatch captured = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        IAuthPermissionProvider source = new IAuthPermissionProvider() {
            public Collection<String> getRoles(User user) {
                Set<String> result = roles.get();
                if (blockOnce.getAndSet(false)) {
                    captured.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }
                return result;
            }
            public Collection<Permission> getPermissions(User user) { return List.of(); }
        };
        RedisAuthPermissionProvider a = new RedisAuthPermissionProvider(source, properties,
                new RedisPermissionCacheStore(properties, transientFailure));
        RedisAuthPermissionProvider b = new RedisAuthPermissionProvider(source, properties, store);
        User user = new User();
        user.setId(42L);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> pending = executor.submit(() -> a.getRoles(user));
            assertTrue(captured.await(5, TimeUnit.SECONDS));
            roles.set(Set.of());
            b.evict(42L);
            release.countDown();
            pending.get(5, TimeUnit.SECONDS);
            assertTrue(b.getRoles(user).isEmpty());
            assertTrue(a.getRoles(user).isEmpty());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private String versionKey() {
        try {
            var method = RedisPermissionCacheStore.class.getDeclaredMethod("versionKey", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, key);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
