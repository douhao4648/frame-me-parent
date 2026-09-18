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
     * 原子轮换 Refresh Token：仅当前存储值仍等于 {@code expectedToken} 时，
     * 才替换为 {@code newToken} 并重置有效期.
     *
     * @param userId       用户 ID
     * @param expectedToken 调用方持有的旧 Refresh Token
     * @param newToken      新 Refresh Token
     * @param expires       新 Token 有效期
     * @return 轮换成功返回 {@code true}；旧 Token 已失效或被并发消费返回 {@code false}
     */
    boolean rotate(Long userId, String expectedToken, String newToken, Duration expires);

    /**
     * 删除用户的 Refresh Token.
     *
     * @param userId 用户 ID
     */
    void delete(Long userId);

    /**
     * 保存上游 IdP token（RP 场景留存，JWT 无 session，故随本存储落地服务端）.
     *
     * <p>默认空实现；不需要 RP 回源能力的存储可不覆盖。</p>
     *
     * @param userId        用户 ID
     * @param appId         上游应用 ID（token 按应用隔离，不同应用互不覆盖）
     * @param upstreamToken 上游 IdP token
     * @param expires       有效期
     */
    default void saveUpstreamToken(Long userId, String appId, String upstreamToken, Duration expires) {
    }

    /**
     * 根据用户 ID + 上游应用 ID 获取上游 IdP token.
     *
     * @param userId 用户 ID
     * @param appId  上游应用 ID
     * @return 上游 token，不存在时返回 {@code null}
     */
    default String getUpstreamToken(Long userId, String appId) {
        return null;
    }

    /**
     * 删除用户的全部上游 IdP token（所有应用，登出/被踢时调用）.
     *
     * @param userId 用户 ID
     */
    default void deleteUpstreamTokens(Long userId) {
    }

    /**
     * 续期用户的全部上游 IdP token（refresh 拉长本地会话时同步调用，
     * 保持"与本地会话同生共死"）.
     *
     * @param userId  用户 ID
     * @param expires 新的有效期
     */
    default void renewUpstreamTokens(Long userId, Duration expires) {
    }
}
