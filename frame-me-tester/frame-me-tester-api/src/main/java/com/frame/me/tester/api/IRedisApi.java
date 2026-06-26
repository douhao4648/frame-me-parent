package com.frame.me.tester.api;

import com.frame.me.api.result.IResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

/**
 * RedisUtils 测试 API 契约.
 */
@Tag(name = "Redis 测试", description = "用于验证 RedisUtils 各项操作")
@HttpExchange("/api/redis")
public interface IRedisApi {

    /**
     * 自检：一次性跑一遍 String/Hash/List/计数/锁等操作并返回每步结果.
     *
     * @return 各操作的执行结果
     */
    @Operation(summary = "自检", description = "依次执行 RedisUtils 核心操作，返回每步结果用于验证连通性与正确性")
    @GetExchange("/self-test")
    IResult<Map<String, Object>> selfTest();

    /**
     * 设置 String 值.
     *
     * @param key   键
     * @param value 值
     * @return 是否成功
     */
    @Operation(summary = "set", description = "设置一个 String 值")
    @PostExchange("/set")
    IResult<Boolean> set(@Parameter(description = "键", required = true) @RequestParam String key,
                         @Parameter(description = "值", required = true) @RequestParam String value);

    /**
     * 获取 String 值.
     *
     * @param key 键
     * @return 值
     */
    @Operation(summary = "get", description = "获取一个 String 值")
    @GetExchange("/get")
    IResult<String> get(@Parameter(description = "键", required = true) @RequestParam String key);

    /**
     * 删除 key.
     *
     * @param key 键
     * @return 是否删除成功
     */
    @Operation(summary = "delete", description = "删除一个 key")
    @PostExchange("/delete")
    IResult<Boolean> delete(@Parameter(description = "键", required = true) @RequestParam String key);

    /**
     * 测试第二个 Redis 实例.
     *
     * @return second 实例的读写结果
     */
    @Operation(summary = "second-test", description = "测试第二个 Redis 实例的读写")
    @GetExchange("/second-test")
    IResult<Map<String, Object>> secondTest();
}