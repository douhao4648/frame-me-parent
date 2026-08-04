package com.frame.me.cloud.shutdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主动下线端点：{@code POST /actuator/offline}.
 *
 * <p>云平台 {@code preStop} 钩子的主路径——SIGTERM 前调用，完成整个下线编排
 * （标记 health DOWN → 反注册 → 等待消费者刷新缓存）后才返回，使 K8s 收到响应后
 * 再发 SIGTERM，时序干净.</p>
 *
 * <p>同步阻塞：调用会阻塞 {@code deregister-wait} 时长（默认 15s），期间正好是
 * 让消费者刷新缓存的时间，返回后 K8s 发 SIGTERM，Tomcat 才开始 graceful shutdown.</p>
 *
 * <p>幂等：重复调用无副作用（flag 已 false、反注册已执行）.</p>
 *
 * <p>需在业务 {@code application.yml} 配置
 * {@code management.endpoints.web.exposure.include} 包含 {@code offline} 才暴露.</p>
 *
 * @author frame-me
 */
@Endpoint(id = "offline")
public class GracefulShutdownEndpoint {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownEndpoint.class);

    private final GracefulShutdownExecutor executor;

    public GracefulShutdownEndpoint(GracefulShutdownExecutor executor) {
        this.executor = executor;
    }

    @WriteOperation
    public Map<String, Object> offline() {
        log.info("收到主动下线请求（preStop），开始优雅下线编排");
        executor.shutdown();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "shutting-down");
        result.put("message", "graceful shutdown initiated, health marked DOWN, deregistered, waited for consumers");
        return result;
    }
}
