package com.frame.me.auth.jwt.core;

import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.redis.util.RedisUtils;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

/**
 * 基于 Redis 的 Refresh Token 存储实现.
 *
 * @author frame-me
 */
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements IRefreshTokenStore {

    private final JwtAuthProperties properties;

    @Override
    public void save(Long userId, String refreshToken, Duration expires) {
        RedisUtils.set(properties.getRefreshTokenPrefix() + userId, refreshToken, expires);
    }

    @Override
    public String get(Long userId) {
        return RedisUtils.get(properties.getRefreshTokenPrefix() + userId);
    }

    @Override
    public void delete(Long userId) {
        RedisUtils.delete(properties.getRefreshTokenPrefix() + userId);
    }
}
