package com.frame.me.base.limit;

import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存登录速率限制器（固定窗口计数，按 IP 隔离）.
 *
 * <p>Redisson 可用时由 {@code RedissonLoginRateLimiter} 自动替换为分布式限流.</p>
 *
 * @author frame-me
 */
@Slf4j
public class InMemoryLoginRateLimiter implements LoginRateLimiter {

    private final ConcurrentHashMap<String, long[]> store = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long windowMs;

    public InMemoryLoginRateLimiter(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.windowMs = window.toMillis();
    }

    @Override
    public void acquire(String clientIp) {
        long now = System.currentTimeMillis();
        long[] entry = store.compute(clientIp, (k, v) -> {
            if (v == null) {
                return new long[]{now, 1};
            }
            if (now - v[0] > windowMs) {
                return new long[]{now, 1};
            }
            if (v[1] >= maxAttempts) {
                return v;
            }
            return new long[]{v[0], v[1] + 1};
        });
        if (entry[1] > maxAttempts) {
            log.warn("登录频率超限(内存): ip={}, attempts={}, windowMs={}", clientIp, entry[1], windowMs);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "登录过于频繁，请稍后再试");
        }
    }
}
