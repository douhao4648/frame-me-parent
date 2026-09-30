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
 * <p>容量防护（攻击者可轮换伪造 IP 头刷 key）：
 * <ul>
 *   <li>容量超 {@link #EVICT_THRESHOLD} 时清理窗口已过期条目，但每窗口最多清一次——
 *       全活跃场景下避免每个请求都 O(n) 全表扫描；</li>
 *   <li>硬上限 {@link #MAX_BUCKETS}：满后拒绝为新 key 建桶（直接限流拒绝），
 *       已有桶不受影响。不淘汰活跃桶——淘汰等于帮攻击者重置其额度。</li>
 * </ul></p>
 *
 * @author frame-me
 */
@Slf4j
public class InMemoryLoginRateLimiter implements LoginRateLimiter {

    /**
     * 触发过期窗口清理的容量阈值.
     */
    private static final int EVICT_THRESHOLD = 10_000;

    /**
     * 桶数量硬上限：达到后拒绝为新 key 建桶.
     */
    private static final int MAX_BUCKETS = 20_000;

    private final ConcurrentHashMap<String, long[]> store = new ConcurrentHashMap<>();
    /** 上次清理时间戳（仅 synchronized 的 {@link #acquire} 内读写，无需原子类）. */
    private long lastCleanupMs;
    private final int maxAttempts;
    private final long windowMs;

    public InMemoryLoginRateLimiter(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.windowMs = window.toMillis();
    }

    @Override
    public synchronized void acquire(String clientIp) {
        long now = System.currentTimeMillis();
        // 定期清理：每窗口最多一次全表扫描（synchronized 保证只放行一个线程清理）
        if (store.size() > EVICT_THRESHOLD && now - lastCleanupMs >= windowMs) {
            lastCleanupMs = now;
            store.entrySet().removeIf(e -> now - e.getValue()[0] > windowMs);
        }
        if (store.size() >= MAX_BUCKETS && !store.containsKey(clientIp)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "登录过于频繁，请稍后再试");
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
