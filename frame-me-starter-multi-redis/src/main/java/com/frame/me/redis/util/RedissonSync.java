package com.frame.me.redis.util;

import org.redisson.RedissonMultiLock;
import org.redisson.RedissonRedLock;
import org.redisson.api.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁与同步原语工具类.
 *
 * <p>在 {@link RedissonLock} 已封装 {@link RLock} 的基础上，进一步提供读写锁、公平锁、
 * 红锁、信号量、门闩等同步能力。所有方法均委托给 {@link RedissonClient}，
 * 由 {@link com.frame.me.redis.config.RedissonLockAutoConfiguration} 在启动时初始化。</p>
 *
 * <p>未引入 Redisson 时，调用本类任何方法都会抛出 {@link IllegalStateException}。</p>
 */
public final class RedissonSync {

    private static RedissonClient redissonClient;

    private RedissonSync() {
    }

    /**
     * 初始化 Redisson 客户端.
     *
     * <p>由 {@link com.frame.me.redis.config.RedissonLockAutoConfiguration} 调用。</p>
     *
     * @param client 默认实例的 Redisson 客户端
     */
    public static void init(RedissonClient client) {
        RedissonSync.redissonClient = client;
    }

    private static void checkInit() {
        if (redissonClient == null) {
            throw new IllegalStateException("Redisson client is not initialized. Please check the me.redis configuration and redisson dependencies");
        }
    }

    // ============================ 读写锁 ============================

    /**
     * 获取指定键的分布式读写锁.
     *
     * @param key 锁键
     * @return RReadWriteLock
     */
    public static RReadWriteLock getReadWriteLock(String key) {
        checkInit();
        return redissonClient.getReadWriteLock(key);
    }

    /**
     * 获取读锁.
     *
     * @param key 锁键
     * @return RLock
     */
    public static RLock readLock(String key) {
        return getReadWriteLock(key).readLock();
    }

    /**
     * 获取写锁.
     *
     * @param key 锁键
     * @return RLock
     */
    public static RLock writeLock(String key) {
        return getReadWriteLock(key).writeLock();
    }

    /**
     * 尝试获取读锁.
     *
     * @param key     锁键
     * @param waitMs  最长等待时间（毫秒）
     * @param leaseMs 锁持有时间（毫秒），{@code <=0} 启用看门狗续期
     * @return 是否获取成功
     */
    public static boolean tryReadLock(String key, long waitMs, long leaseMs) {
        try {
            return readLock(key).tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 尝试获取写锁.
     *
     * @param key     锁键
     * @param waitMs  最长等待时间（毫秒）
     * @param leaseMs 锁持有时间（毫秒），{@code <=0} 启用看门狗续期
     * @return 是否获取成功
     */
    public static boolean tryWriteLock(String key, long waitMs, long leaseMs) {
        try {
            return writeLock(key).tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放当前线程持有的读锁.
     *
     * @param key 锁键
     */
    public static void unlockRead(String key) {
        RLock lock = readLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 释放当前线程持有的写锁.
     *
     * @param key 锁键
     */
    public static void unlockWrite(String key) {
        RLock lock = writeLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    // ============================ 公平锁 ============================

    /**
     * 获取指定键的公平锁.
     *
     * @param key 锁键
     * @return RLock（公平锁）
     */
    public static RLock getFairLock(String key) {
        checkInit();
        return redissonClient.getFairLock(key);
    }

    /**
     * 尝试获取公平锁.
     *
     * @param key     锁键
     * @param waitMs  最长等待时间（毫秒）
     * @param leaseMs 锁持有时间（毫秒），{@code <=0} 启用看门狗续期
     * @return 是否获取成功
     */
    public static boolean tryFairLock(String key, long waitMs, long leaseMs) {
        try {
            return getFairLock(key).tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放当前线程持有的公平锁.
     *
     * @param key 锁键
     */
    public static void unlockFair(String key) {
        RLock lock = getFairLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    // ============================ 红锁 / 联锁 ============================

    /**
     * 获取红锁（RedLock 算法，需多数节点同意）.
     *
     * <p>每个 key 会在当前 RedissonClient 上创建独立的 {@link RLock}。</p>
     *
     * <p><b>已弃用</b>：Redisson 4.x 中 {@link RedissonRedLock} 整体被标记为 {@code @Deprecated}，
     * RedLock 算法不再被推荐。如需多锁原子性，请改用 {@link #getMultiLock(String...)}；
     * 如需更强的一致性锁，请改用 {@code redissonClient.getFencedLock(key)} 的 {@link RFencedLock}。</p>
     *
     * @param keys 锁键列表
     * @return RedissonRedLock
     */
    @Deprecated
    public static RedissonRedLock getRedLock(String... keys) {
        checkInit();
        RLock[] locks = Arrays.stream(keys)
                .map(redissonClient::getLock)
                .toArray(RLock[]::new);
        return new RedissonRedLock(locks);
    }

    /**
     * 获取联锁（必须同时获取所有锁）.
     *
     * @param keys 锁键列表
     * @return RedissonMultiLock
     */
    public static RedissonMultiLock getMultiLock(String... keys) {
        checkInit();
        RLock[] locks = Arrays.stream(keys)
                .map(redissonClient::getLock)
                .toArray(RLock[]::new);
        return new RedissonMultiLock(locks);
    }

    // ============================ 信号量 ============================

    /**
     * 获取指定键的信号量.
     *
     * @param key 键
     * @return RSemaphore
     */
    public static RSemaphore getSemaphore(String key) {
        checkInit();
        return redissonClient.getSemaphore(key);
    }

    /**
     * 尝试获取信号量许可.
     *
     * @param key     键
     * @param permits 许可数量
     * @param waitMs  最长等待时间（毫秒）
     * @return 是否获取成功
     */
    public static boolean tryAcquire(String key, int permits, long waitMs) {
        try {
            return getSemaphore(key).tryAcquire(permits, Duration.ofMillis(waitMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放信号量许可.
     *
     * @param key     键
     * @param permits 许可数量
     */
    public static void release(String key, int permits) {
        getSemaphore(key).release(permits);
    }

    // ============================ 门闩 ============================

    /**
     * 获取指定键的倒计时门闩.
     *
     * @param key 键
     * @return RCountDownLatch
     */
    public static RCountDownLatch getCountDownLatch(String key) {
        checkInit();
        return redissonClient.getCountDownLatch(key);
    }

    /**
     * 设置门闩初始计数.
     *
     * @param key   键
     * @param count 计数
     * @return 是否设置成功
     */
    public static boolean trySetCount(String key, long count) {
        return getCountDownLatch(key).trySetCount(count);
    }

    /**
     * 等待门闩归零.
     *
     * @param key    键
     * @param waitMs 最长等待时间（毫秒），{@code <=0} 表示一直等待
     * @return 是否归零
     */
    public static boolean await(String key, long waitMs) {
        try {
            RCountDownLatch latch = getCountDownLatch(key);
            if (waitMs <= 0) {
                latch.await();
                return true;
            }
            return latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 门闩计数减一.
     *
     * @param key 键
     */
    public static void countDown(String key) {
        getCountDownLatch(key).countDown();
    }

    // ============================ 可过期信号量 ============================

    /**
     * 获取可过期信号量.
     *
     * @param key 键
     * @return RPermitExpirableSemaphore
     */
    public static RPermitExpirableSemaphore getPermitExpirableSemaphore(String key) {
        checkInit();
        return redissonClient.getPermitExpirableSemaphore(key);
    }

    /**
     * 尝试获取可过期许可.
     *
     * @param key     键
     * @param waitMs  最长等待时间（毫秒）
     * @param leaseMs 许可租期（毫秒）
     * @return 许可标识，未获取到返回 {@code null}
     */
    public static String tryAcquireWithExpiry(String key, long waitMs, long leaseMs) {
        try {
            return getPermitExpirableSemaphore(key).tryAcquire(waitMs, leaseMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 释放可过期许可.
     *
     * @param key      键
     * @param permitId 许可标识
     * @return 是否释放成功
     */
    public static boolean release(String key, String permitId) {
        return getPermitExpirableSemaphore(key).tryRelease(permitId);
    }
}
