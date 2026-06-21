package com.frame.me.tester.api;

import com.frame.me.api.result.IResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 健康检查 API 契约.
 */
@Tag(name = "健康检查", description = "服务健康检查相关接口")
@HttpExchange("/api/health")
public interface IHealthApi {

    /**
     * 健康检查.
     *
     * @return 健康状态文本
     */
    @Operation(summary = "健康检查", description = "返回服务健康状态，触发异常可用于验证全局异常处理链路")
    @GetExchange
    IResult<String> health();
}
