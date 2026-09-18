package com.frame.me.redis.util;

import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.limit.LoginRateLimiter;
import com.frame.me.base.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;

import java.time.Duration;

/**
 * 基于 Redisson RRateLimiter 的分布式登录限流器.
 *
 * <p>classpath 存在 Redisson 时自动覆盖 {@code InMemoryLoginRateLimiter}，
 * 多实例共享同一个 Redis 计数器，负载均衡下不会绕过.</p>
 *
 * @author frame-me
 */
@Slf4j
public class RedissonLoginRateLimiter implements LoginRateLimiter {

    private static final String KEY_PREFIX = "login:rate:";
    private final RedissonLimiter redissonLimiter;
    private final int maxAttempts;
    private final Duration window;

    public RedissonLoginRateLimiter(RedissonLimiter redissonLimiter, int maxAttempts, Duration window) {
        this.redissonLimiter = redissonLimiter;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    @Override
    public void acquire(String clientIp) {
        RRateLimiter limiter = redissonLimiter.getRateLimiter(KEY_PREFIX + clientIp);
        // 首次或速率变更时尝试设置速率；新建的限流器挂 2 倍窗口 TTL——闲置 key 自动淘汰，
        // 攻击者轮换伪造 IP 头刷 key 不会永久占用 Redis 内存。
        // 不削弱防护：RRateLimiter 按速率持续补充令牌，key 回收重建后配额仍受同一速率约束
        if (limiter.trySetRate(RateType.OVERALL, maxAttempts, window)) {
            limiter.expire(window.multipliedBy(2));
        }
        if (!limiter.tryAcquire()) {
            log.warn("登录频率超限(Redis): ip={}, maxAttempts={}, window={}", clientIp, maxAttempts, window);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "登录过于频繁，请稍后再试");
        }
    }
}
