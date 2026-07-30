package com.frame.me.tester.controller;

import com.frame.me.api.result.IResult;
import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.base.result.Result;
import com.frame.me.tester.api.IHealthApi;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查端点，匿名可访问（供 LB / 监控探针直连）.
 */
@Anonymous
@RestController
public class HealthController implements IHealthApi {

    @Override
    public IResult<String> health() {
        return Result.success("UP");
    }
}
