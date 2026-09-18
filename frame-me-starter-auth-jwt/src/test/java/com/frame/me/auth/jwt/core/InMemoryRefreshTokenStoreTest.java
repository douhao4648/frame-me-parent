package com.frame.me.auth.jwt.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InMemoryRefreshTokenStore} 单元测试.
 *
 * @author frame-me
 */
class InMemoryRefreshTokenStoreTest {

    private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

    @Test
    void saveAndGet() {
        store.save(1L, "token-1", Duration.ofMinutes(30));
        assertEquals("token-1", store.get(1L));
    }

    @Test
    void getReturnsNullWhenExpired() {
        store.save(1L, "token-1", Duration.ofSeconds(-1));
        assertNull(store.get(1L));
        // 过期条目被惰性清除
        store.save(1L, "token-2", Duration.ofMinutes(30));
        assertEquals("token-2", store.get(1L));
    }

    @Test
    void delete() {
        store.save(1L, "token-1", Duration.ofMinutes(30));
        store.delete(1L);
        assertNull(store.get(1L));
    }

    @Test
    void getMissingReturnsNull() {
        assertNull(store.get(99L));
    }

    @Test
    void rotateReplacesOnlyMatchingCurrentToken() {
        store.save(1L, "old-token", Duration.ofMinutes(30));

        assertTrue(store.rotate(1L, "old-token", "new-token", Duration.ofMinutes(30)));
        assertEquals("new-token", store.get(1L));
        assertFalse(store.rotate(1L, "old-token", "attacker-token", Duration.ofMinutes(30)));
        assertEquals("new-token", store.get(1L));
    }
}
