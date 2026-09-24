package com.frame.me.tester.controller;

import com.frame.me.api.result.IResult;
import com.frame.me.base.result.Result;
import com.frame.me.redis.util.RedisClient;
import com.frame.me.redis.util.RedisClientRegistry;
import com.frame.me.redis.util.RedissonLock;
import com.frame.me.tester.api.IRedisApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Redis 客户端测试 Controller.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnBean({RedisClientRegistry.class, RedissonLock.class})
public class RedisController implements IRedisApi {

    private final RedisClientRegistry redisClients;
    private final RedissonLock redissonLock;

    @Override
    public IResult<Map<String, Object>> selfTest() {
        Map<String, Object> r = new LinkedHashMap<>();
        String prefix = "redis:selftest:";
        RedisClient redis = redisClients.getDefaultClient();

        // String
        redis.set(prefix + "str", "hello", Duration.ofMinutes(1));
        r.put("string", redis.get(prefix + "str"));

        // Counter
        redis.delete(prefix + "counter");
        redis.increment(prefix + "counter", 5);
        r.put("counter", redis.decrement(prefix + "counter", 2));

        // Hash
        redis.hSet(prefix + "hash", "field", "value");
        r.put("hash", redis.hGet(prefix + "hash", "field"));

        // List
        redis.delete(prefix + "list");
        redis.rPush(prefix + "list", "a");
        redis.rPush(prefix + "list", "b");
        r.put("list", redis.lRange(prefix + "list", 0, -1));

        // Lock
        String token = UUID.randomUUID().toString();
        boolean locked = redis.tryLock(prefix + "lock", token, 5000);
        boolean unlocked = redis.unlock(prefix + "lock", token);
        r.put("lock", "lock=" + locked + ",unlock=" + unlocked);

        // 清理
        redis.delete(java.util.List.of(prefix + "str", prefix + "counter", prefix + "hash", prefix + "list"));

        return Result.success(r);
    }

    /** 演示用 key 前缀，隔离业务数据，防任意 key 覆盖. */
    private static final String DEMO_KEY_PREFIX = "redis:demo:";

    @Override
    public IResult<Boolean> set(String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return Result.error(com.frame.me.base.result.ResultCode.BAD_REQUEST, "key/value 不能为空");
        }
        String safeKey = DEMO_KEY_PREFIX + key;
        redisClients.getDefaultClient().set(safeKey, value);
        redisClients.getClient("second").set(safeKey + ":second", value);
        return Result.success(true);
    }

    @Override
    public IResult<String> get(String key) {
        if (key == null || key.isBlank()) {
            return Result.error(com.frame.me.base.result.ResultCode.BAD_REQUEST, "key 不能为空");
        }
        return Result.success(redisClients.getDefaultClient().get(DEMO_KEY_PREFIX + key));
    }

    @Override
    public IResult<Boolean> delete(String key) {
        if (key == null || key.isBlank()) {
            return Result.error(com.frame.me.base.result.ResultCode.BAD_REQUEST, "key 不能为空");
        }
        return Result.success(redisClients.getDefaultClient().delete(DEMO_KEY_PREFIX + key));
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
        boolean firstLock = redissonLock.tryLock(key, 0, 5000);
        boolean reentrantLock = redissonLock.tryLock(key, 0, 5000);
        r.put("firstLock", firstLock);
        r.put("reentrantLock", reentrantLock);

        // 互斥性：在另一个线程尝试获取同一把锁，应失败（虚拟线程，JVM 托管，非手动平台线程）
        boolean[] otherThreadAcquired = {false};
        Thread t = Thread.startVirtualThread(
                () -> otherThreadAcquired[0] = redissonLock.tryLock(key, 100, 100));
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        r.put("otherThreadAcquired", otherThreadAcquired[0]);

        // 释放两次（对应两次可重入获取）
        redissonLock.unlock(key);
        redissonLock.unlock(key);

        // 释放后应能重新获取
        boolean reacquired = redissonLock.tryLock(key, 0, 1000);
        r.put("reacquiredAfterUnlock", reacquired);
        redissonLock.unlock(key);

        return Result.success(r);
    }
}
