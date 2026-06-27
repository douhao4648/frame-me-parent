package com.frame.me.tester.controller;

import com.frame.me.api.result.IResult;
import com.frame.me.base.result.Result;
import com.frame.me.redis.util.RedissonLock;
import com.frame.me.redis.util.RedisUtils;
import com.frame.me.tester.api.IRedisApi;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RedisUtils 测试 Controller.
 */
@RestController
public class RedisController implements IRedisApi {

    @Override
    public IResult<Map<String, Object>> selfTest() {
        Map<String, Object> r = new LinkedHashMap<>();
        String prefix = "redis:selftest:";

        // String
        RedisUtils.set(prefix + "str", "hello", Duration.ofMinutes(1));
        r.put("string", RedisUtils.get(prefix + "str"));

        // Counter
        RedisUtils.delete(prefix + "counter");
        RedisUtils.increment(prefix + "counter", 5);
        r.put("counter", RedisUtils.decrement(prefix + "counter", 2));

        // Hash
        RedisUtils.hSet(prefix + "hash", "field", "value");
        r.put("hash", RedisUtils.hGet(prefix + "hash", "field"));

        // List
        RedisUtils.delete(prefix + "list");
        RedisUtils.rPush(prefix + "list", "a");
        RedisUtils.rPush(prefix + "list", "b");
        r.put("list", RedisUtils.lRange(prefix + "list", 0, -1));

        // Lock
        String token = UUID.randomUUID().toString();
        boolean locked = RedisUtils.tryLock(prefix + "lock", token, 5000);
        boolean unlocked = RedisUtils.unlock(prefix + "lock", token);
        r.put("lock", "lock=" + locked + ",unlock=" + unlocked);

        // 清理
        RedisUtils.delete(java.util.List.of(prefix + "str", prefix + "counter", prefix + "hash", prefix + "list"));

        return Result.success(r);
    }

    @Override
    public IResult<Boolean> set(String key, String value) {
        RedisUtils.set(key, value);
        RedisUtils.getClient("second").set(key+":second", value);
        return Result.success(true);
    }

    @Override
    public IResult<String> get(String key) {
        return Result.success(RedisUtils.get(key));
    }

    @Override
    public IResult<Boolean> delete(String key) {
        return Result.success(RedisUtils.delete(key));
    }

    /**
     * 测试 Redisson 分布式锁.
     *
     * @return 锁测试结果
     */
    @Override
    public IResult<Map<String, Object>> redisLockTest() {
        Map<String, Object> r = new LinkedHashMap<>();
        // 每次调用用唯一 key，避免并发/重复调用时互相污染锁状态
        String key = "redis:lock:test:" + UUID.randomUUID();

        // 可重入锁
        boolean firstLock = RedissonLock.tryLock(key, 0, 5000);
        boolean reentrantLock = RedissonLock.tryLock(key, 0, 5000);
        r.put("firstLock", firstLock);
        r.put("reentrantLock", reentrantLock);

        // 互斥性：在另一个线程尝试获取同一把锁，应失败
        boolean[] otherThreadAcquired = {false};
        Thread t = new Thread(() -> otherThreadAcquired[0] = RedissonLock.tryLock(key, 100, 100));
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        r.put("otherThreadAcquired", otherThreadAcquired[0]);

        // 释放两次（对应两次可重入获取）
        RedissonLock.unlock(key);
        RedissonLock.unlock(key);

        // 释放后应能重新获取
        boolean reacquired = RedissonLock.tryLock(key, 0, 1000);
        r.put("reacquiredAfterUnlock", reacquired);
        RedissonLock.unlock(key);

        return Result.success(r);
    }
}
