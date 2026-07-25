package com.frame.me.auth.jwt.core;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 内存 Refresh Token 存储，仅用于测试.
 *
 * @author frame-me
 */
public class InMemoryRefreshTokenStore implements IRefreshTokenStore {

    private final Map<Long, String> store = new HashMap<>();

    @Override
    public void save(Long userId, String refreshToken, Duration expires) {
        store.put(userId, refreshToken);
    }

    @Override
    public String get(Long userId) {
        return store.get(userId);
    }

    @Override
    public void delete(Long userId) {
        store.remove(userId);
    }
}
