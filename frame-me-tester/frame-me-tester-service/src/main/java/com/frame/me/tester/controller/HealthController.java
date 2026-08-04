package com.frame.me.tester.controller;

import com.frame.me.api.result.IResult;
import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import com.frame.me.cloud.shutdown.ShutdownReadyFlag;
import com.frame.me.tester.api.IHealthApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查端点，匿名可访问（供 LB / 监控探针直连）.
 *
 * <p>注入 {@link ShutdownReadyFlag} 联动：服务就绪返回 UP，进入优雅下线（flag=false）
 * 返回 503 DOWN，使打业务端口的 LB 探针立即失败、停止发新流量.</p>
 */
@Anonymous
@RestController
@RequiredArgsConstructor
public class HealthController implements IHealthApi {

    private final ShutdownReadyFlag shutdownReadyFlag;

    @Override
    public IResult<String> health() {
        if (shutdownReadyFlag.isReady()) {
            return Result.success("UP");
        }
        return Result.error(ResultCode.SERVICE_UNAVAILABLE, "DOWN");
    }
}
