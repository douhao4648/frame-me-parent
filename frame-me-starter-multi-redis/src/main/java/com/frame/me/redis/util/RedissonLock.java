package com.frame.me.redis.util;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁客户端.
 *
 * <p>基于 Redisson {@link RLock} 实现，提供可重入锁与看门狗自动续期，仅作用于默认 Redis 实例。
 * 仅当 classpath 引入 Redisson 时由 {@link com.frame.me.redis.config.RedissonAutoConfiguration}
 * 注册为 Bean；未引入时请改用
 * {@link com.frame.me.redis.util.RedisClient#tryLock} 的简单锁。</p>
 */
public final class RedissonLock {

    private final RedissonClient redissonClient;

    public RedissonLock(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient");
    }

    /**
     * 获取指定键的可重入锁对象.
     *
     * @param key 锁键
     * @return RLock
     */
    public RLock getLock(String key) {
        return redissonClient.getLock(key);
    }

    /**
     * 尝试获取分布式锁.
     *
     * <p>{@code leaseMs <= 0} 时启用看门狗自动续期（持锁期间锁不会因超时被释放，调用方负责 {@link #unlock(String)}）；
     * 否则锁在 {@code leaseMs} 毫秒后自动释放。</p>
     *
     * @param key     锁键
     * @param waitMs  最长等待获取时间（毫秒）
     * @param leaseMs 锁持有时间（毫秒），{@code <=0} 表示启用看门狗续期
     * @return 是否获取成功
     */
    public boolean tryLock(String key, long waitMs, long leaseMs) {
        try {
            return getLock(key).tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放分布式锁.
     *
     * <p>仅释放当前线程持有的锁；未持有时静默返回，避免误抛 {@link IllegalMonitorStateException}。</p>
     *
     * @param key 锁键
     */
    public void unlock(String key) {
        RLock lock = getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
