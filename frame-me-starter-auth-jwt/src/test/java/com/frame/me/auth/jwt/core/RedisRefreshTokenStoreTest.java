package com.frame.me.auth.jwt.core;

import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.redis.util.RedisClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisRefreshTokenStore} 原子轮换契约测试.
 *
 * @author frame-me
 */
class RedisRefreshTokenStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void rotateReturnsLuaCompareAndSetResult() {
        JwtAuthProperties properties = new JwtAuthProperties();
        properties.setRefreshTokenPrefix("refresh:");
        RedisClient redisClient = mock(RedisClient.class);
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(properties, redisClient);
        Duration expires = Duration.ofMinutes(30);

        when(redisClient.executeScript(
                any(RedisScript.class),
                eq(Collections.singletonList("refresh:1")),
                eq("old-token"), eq("new-token"), eq(String.valueOf(expires.toMillis()))))
                .thenReturn(1L, 0L);

        assertTrue(store.rotate(1L, "old-token", "new-token", expires));
        assertFalse(store.rotate(1L, "old-token", "new-token", expires));
        verify(redisClient, org.mockito.Mockito.times(2)).executeScript(
                any(RedisScript.class),
                eq(Collections.singletonList("refresh:1")),
                eq("old-token"), eq("new-token"), eq(String.valueOf(expires.toMillis())));
    }
}
