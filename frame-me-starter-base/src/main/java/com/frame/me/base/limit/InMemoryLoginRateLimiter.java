package com.frame.me.base.limit;

import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存登录速率限制器（固定窗口计数，按 key 隔离——调用方传 IP 或账号维度 key）.
 *
 * <p>Redisson 可用时由 {@code RedissonLoginRateLimiter} 自动替换为分布式限流.</p>
 *
 * <p>淘汰：map 条目随 key 无限增长（攻击者可轮换伪造 IP 头刷 key），故容量超
 * {@link #EVICT_THRESHOLD} 时顺手清掉窗口已过期的条目；极端全活跃场景下 map 有界于
 * 「阈值 + 窗口内活跃 key 数」，不会无限膨胀.</p>
 *
 * @author frame-me
 */
@Slf4j
public class InMemoryLoginRateLimiter implements LoginRateLimiter {

    /**
     * 触发过期窗口清理的容量阈值.
     */
    private static final int EVICT_THRESHOLD = 10_000;

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
        if (store.size() > EVICT_THRESHOLD) {
            store.entrySet().removeIf(e -> now - e.getValue()[0] > windowMs);
        }
        long[] entry = store.compute(clientIp, (k, v) -> {
            if (v == null) {
                return new long[]{now, 1};
            }
            if (now - v[0] > windowMs) {
                return new long[]{now, 1};
            }
            if (v[1] > maxAttempts) {
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
