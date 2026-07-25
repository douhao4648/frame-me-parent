package com.frame.me.auth.jwt.core;

import java.time.Duration;

/**
 * Refresh Token 存储接口.
 *
 * @author frame-me
 */
public interface IRefreshTokenStore {

    /**
     * 保存 Refresh Token.
     *
     * @param userId       用户 ID
     * @param refreshToken Refresh Token
     * @param expires      有效期
     */
    void save(Long userId, String refreshToken, Duration expires);

    /**
     * 根据用户 ID 获取 Refresh Token.
     *
     * @param userId 用户 ID
     * @return Refresh Token，不存在时返回 {@code null}
     */
    String get(Long userId);

    /**
     * 删除用户的 Refresh Token.
     *
     * @param userId 用户 ID
     */
    void delete(Long userId);
}
