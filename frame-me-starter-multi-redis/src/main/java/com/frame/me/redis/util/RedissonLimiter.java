package com.frame.me.redis.util;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * Redisson 分布式限流工具类.
 *
 * <p>基于 {@link RRateLimiter} 提供令牌桶限流能力，支持全集群共享或每客户端独立速率。</p>
 */
public final class RedissonLimiter {

    private static RedissonClient redissonClient;

    private RedissonLimiter() {
    }

    /**
     * 初始化 Redisson 客户端.
     *
     * <p>由 {@link com.frame.me.redis.config.RedissonLockAutoConfiguration} 调用。</p>
     *
     * @param client 默认实例的 Redisson 客户端
     */
    public static void init(RedissonClient client) {
        RedissonLimiter.redissonClient = client;
    }

    private static void checkInit() {
        if (redissonClient == null) {
            throw new IllegalStateException("Redisson client is not initialized. Please check the frame.me.redis configuration and redisson dependencies");
        }
    }

    /**
     * 获取限流器.
     *
     * @param key 键
     * @return RRateLimiter
     */
    public static RRateLimiter getRateLimiter(String key) {
        checkInit();
        return redissonClient.getRateLimiter(key);
    }

    /**
     * 初始化限流器速率.
     *
     * @param key              键
     * @param type             速率类型
     * @param rate             速率
     * @param rateInterval     速率间隔
     * @param rateIntervalUnit 速率间隔单位
     * @return 是否初始化成功
     */
    public static boolean trySetRate(String key, RateType type, long rate,
                                     long rateInterval, java.util.concurrent.TimeUnit rateIntervalUnit) {
        return getRateLimiter(key).trySetRate(type, rate, Duration.ofMillis(rateIntervalUnit.toMillis(rateInterval)));
    }

    /**
     * 尝试获取一个许可.
     *
     * @param key 键
     * @return 是否获取成功
     */
    public static boolean tryAcquire(String key) {
        return getRateLimiter(key).tryAcquire();
    }

    /**
     * 尝试获取指定数量许可.
     *
     * @param key     键
     * @param permits 许可数量
     * @return 是否获取成功
     */
    public static boolean tryAcquire(String key, long permits) {
        return getRateLimiter(key).tryAcquire(permits);
    }

    /**
     * 在指定超时时间内尝试获取许可.
     *
     * @param key     键
     * @param permits 许可数量
     * @param waitMs  最长等待时间（毫秒）
     * @return 是否获取成功
     */
    public static boolean tryAcquire(String key, long permits, long waitMs) {
        return getRateLimiter(key).tryAcquire(permits, Duration.ofMillis(waitMs));
    }

    /**
     * 查询剩余可用许可数.
     *
     * @param key 键
     * @return 可用许可数
     */
    public static long availablePermits(String key) {
        return getRateLimiter(key).availablePermits();
    }
}
