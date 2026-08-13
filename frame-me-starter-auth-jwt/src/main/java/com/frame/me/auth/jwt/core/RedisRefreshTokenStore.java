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

    /**
     * 上游 token 存 Redis hash：key = 前缀 + userId，field = appId，value = token.
     *
     * <p>选 hash 而非把 appId 拼进 key：refresh 续期（{@code expire}）与登出删除
     * （{@code delete}）只需操作用户级单 key，无需 SCAN；不同应用的 token 在同 key 下
     * 各占 field，共用 Redis 的多个下游结构性免疫互撞。TTL 挂在整个 hash 上按用户共享，
     * 任一 app 写入/续期即全体续期——单服务单 app 场景无差异，多 app 共 hash 时可接受。</p>
     *
     * <p>用 {@link RedisUtils#hSetWithExpire} 原子地 HSET + PEXPIRE：避免 hSet 成功后 expire
     * 失败导致 hash 无 TTL 永久驻留（bearer 凭证超期留存），与 {@code save(refreshToken)}
     * 的原子 {@code SET ... EX} 语义对齐.</p>
     */
    @Override
    public void saveUpstreamToken(Long userId, String appId, String upstreamToken, Duration expires) {
        RedisUtils.hSetWithExpire(properties.getUpstreamTokenPrefix() + userId, appId, upstreamToken, expires);
    }

    /**
     * 读取上游 token.
     *
     * <p>用带 {@code Class} 参数的 {@link RedisUtils#hGet(String, String, Class)} 反序列化——
     * {@code hSet} 侧用 {@code JSON.toJSONString} 序列化存入（String 值会带引号），
     * 读侧必须用 {@code JSON.parseObject} 还原，否则拿到的是带引号的 JSON 字符串
     * 而非裸 token，下游据此调 SSO {@code /userinfo} 会 401.</p>
     */
    @Override
    public String getUpstreamToken(Long userId, String appId) {
        return RedisUtils.hGet(properties.getUpstreamTokenPrefix() + userId, appId, String.class);
    }

    @Override
    public void deleteUpstreamTokens(Long userId) {
        RedisUtils.delete(properties.getUpstreamTokenPrefix() + userId);
    }

    @Override
    public void renewUpstreamTokens(Long userId, Duration expires) {
        RedisUtils.expire(properties.getUpstreamTokenPrefix() + userId, expires);
    }
}
