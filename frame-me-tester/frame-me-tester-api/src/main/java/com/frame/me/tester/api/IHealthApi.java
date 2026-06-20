package com.frame.me.tester.api;

import com.frame.me.api.result.IResult;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 健康检查 API 契约.
 */
@HttpExchange("/health")
public interface IHealthApi {

    /**
     * 健康检查.
     *
     * @return 健康状态文本
     */
    @GetExchange
    IResult<String> health();
}
