//package com.frame.me.tester.controller;
//
//import com.frame.me.api.result.IResult;
//import com.frame.me.base.result.Result;
//import com.frame.me.redis.util.RedisUtils;
//import com.frame.me.tester.api.IRedisApi;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.time.Duration;
//import java.util.LinkedHashMap;
//import java.util.Map;
//import java.util.UUID;
//
///**
// * RedisUtils 测试 Controller.
// */
//@RestController
//public class RedisController implements IRedisApi {
//
//    @Override
//    public IResult<Map<String, Object>> selfTest() {
//        Map<String, Object> r = new LinkedHashMap<>();
//        String prefix = "redis:selftest:";
//
//        // String
//        RedisUtils.set(prefix + "str", "hello", Duration.ofMinutes(1));
//        r.put("string", RedisUtils.get(prefix + "str"));
//
//        // Counter
//        RedisUtils.delete(prefix + "counter");
//        RedisUtils.increment(prefix + "counter", 5);
//        r.put("counter", RedisUtils.decrement(prefix + "counter", 2));
//
//        // Hash
//        RedisUtils.hSet(prefix + "hash", "field", "value");
//        r.put("hash", RedisUtils.hGet(prefix + "hash", "field"));
//
//        // List
//        RedisUtils.delete(prefix + "list");
//        RedisUtils.rPush(prefix + "list", "a");
//        RedisUtils.rPush(prefix + "list", "b");
//        r.put("list", RedisUtils.lRange(prefix + "list", 0, -1));
//
//        // Lock
//        String token = UUID.randomUUID().toString();
//        boolean locked = RedisUtils.tryLock(prefix + "lock", token, 5000);
//        boolean unlocked = RedisUtils.unlock(prefix + "lock", token);
//        r.put("lock", "lock=" + locked + ",unlock=" + unlocked);
//
//        // 清理
//        RedisUtils.delete(java.util.List.of(
//                prefix + "str", prefix + "counter", prefix + "hash", prefix + "list"));
//
//        return Result.success(r);
//    }
//
//    @Override
//    public IResult<Boolean> set(String key, String value) {
//        RedisUtils.set(key, value);
//        return Result.success(true);
//    }
//
//    @Override
//    public IResult<String> get(String key) {
//        return Result.success(RedisUtils.get(key));
//    }
//
//    @Override
//    public IResult<Boolean> delete(String key) {
//        return Result.success(RedisUtils.delete(key));
//    }
//}
