package com.frame.me.auth.rbac.redis.store;

import com.frame.me.auth.rbac.redis.UserPermissionSnapshot;
import com.frame.me.auth.rbac.redis.config.RbacRedisProperties;
import com.frame.me.redis.util.RedisClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RedisPermissionCacheStore} 版本围栏接线测试：版本读取、
 * 条件写入（Lua compare-and-set）与吊销原子段（DEL+INCR）的契约.
 *
 * @author frame-me
 */
class RedisPermissionCacheStoreTest {

    /**
     * RedisClient 替身：只覆盖 get/executeScript，不触模板.
     */
    @SuppressWarnings("unchecked")
    static class StubRedisClient extends RedisClient {
        String getResult;
        RuntimeException getError;
        Object scriptResult;
        RuntimeException scriptError;
        List<String> scriptKeys;
        Object[] scriptArgs;

        StubRedisClient() {
            super(null, null);
        }

        @Override
        public String get(String key) {
            if (getError != null) {
                throw getError;
            }
            return getResult;
        }

        @Override
        public <T> T executeScript(RedisScript<T> script, List<String> keys, Object... args) {
            if (scriptError != null) {
                throw scriptError;
            }
            if (script.getResultType() == String.class && getError != null) {
                throw getError;
            }
            scriptKeys = keys;
            scriptArgs = args;
            if (script.getResultType() == String.class) {
                return (T) (getResult == null ? args[0] : getResult);
            }
            return (T) scriptResult;
        }
    }

    private static RedisPermissionCacheStore store(StubRedisClient client) {
        return new RedisPermissionCacheStore(new RbacRedisProperties(), client);
    }

    @Test
    void version_noRecord_createsPositiveGeneration() {
        assertTrue(store(new StubRedisClient()).version("auth:perms:1") > 0);
    }

    @Test
    void version_existingNumber_parsed() {
        StubRedisClient client = new StubRedisClient();
        client.getResult = "3";
        assertEquals(3, store(client).version("auth:perms:1"));
    }

    @Test
    void version_readError_returnsMinusOneAsUnavailable() {
        StubRedisClient client = new StubRedisClient();
        client.getError = new IllegalStateException("redis down");
        assertEquals(-1, store(client).version("auth:perms:1"));
    }

    @Test
    void setIfVersionMatches_written_returnsTrueWithFencingArgs() {
        StubRedisClient client = new StubRedisClient();
        client.scriptResult = 1L;

        boolean written = store(client).setIfVersionMatches("auth:perms:1",
                new UserPermissionSnapshot(), Duration.ofMinutes(30), 5);

        assertTrue(written);
        assertEquals(client.scriptKeys.get(0) + ":ver", client.scriptKeys.get(1));
        assertEquals(io.lettuce.core.cluster.SlotHash.getSlot(client.scriptKeys.get(0)),
                io.lettuce.core.cluster.SlotHash.getSlot(client.scriptKeys.get(1)));
        assertEquals("5", client.scriptArgs[0], "回源前读到的版本作为期望值");
        assertEquals(String.valueOf(Duration.ofMinutes(30).toMillis()), client.scriptArgs[2]);
    }

    @Test
    void setIfVersionMatches_versionMismatch_returnsFalse() {
        StubRedisClient client = new StubRedisClient();
        client.scriptResult = 0L;
        assertFalse(store(client).setIfVersionMatches("auth:perms:1",
                new UserPermissionSnapshot(), Duration.ofMinutes(30), 5));
    }

    @Test
    void setIfVersionMatches_error_degradesToFalse() {
        StubRedisClient client = new StubRedisClient();
        client.scriptError = new IllegalStateException("redis down");
        assertFalse(store(client).setIfVersionMatches("auth:perms:1",
                new UserPermissionSnapshot(), Duration.ofMinutes(30), 5));
    }

    @Test
    void bumpVersion_invokesAtomicDeleteAndReplacementWithVersionTtl() {
        StubRedisClient client = new StubRedisClient();
        client.scriptResult = 1L;

        store(client).bumpVersion("auth:perms:1");

        assertEquals(client.scriptKeys.get(0) + ":ver", client.scriptKeys.get(1));
        assertTrue(Long.parseLong(client.scriptArgs[0].toString()) > 0);
        assertEquals(String.valueOf(Duration.ofMinutes(30).toMillis()), client.scriptArgs[1],
                "版本键 TTL 默认对齐缓存 TTL");
    }

    @Test
    void bumpVersion_error_propagatesAsSecurityAction() {
        StubRedisClient client = new StubRedisClient();
        client.scriptError = new IllegalStateException("redis down");
        assertThrows(IllegalStateException.class, () -> store(client).bumpVersion("auth:perms:1"));
    }

    @Test
    void bumpVersion_missingConfirmation_propagates() {
        assertThrows(IllegalStateException.class, () -> store(new StubRedisClient()).bumpVersion("auth:perms:1"));
    }
}
