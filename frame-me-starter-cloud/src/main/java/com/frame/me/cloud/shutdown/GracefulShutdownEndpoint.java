package com.frame.me.cloud.shutdown;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
 * <p>访问控制：配置 {@code me.cloud.shutdown.endpoint-token} 后，
 * 请求头 {@code X-Offline-Token} 必须匹配否则 403（preStop 脚本 curl 带上）。
 * 未配置时放行但每次调用 WARN——本端点是远程下线开关，生产必须配 token.</p>
 *
 * @author frame-me
 */
@Endpoint(id = "offline")
public class GracefulShutdownEndpoint {

    /**
     * 共享密钥请求头名.
     */
    public static final String TOKEN_HEADER = "X-Offline-Token";

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownEndpoint.class);

    private final GracefulShutdownExecutor executor;
    private final GracefulShutdownProperties properties;

    public GracefulShutdownEndpoint(GracefulShutdownExecutor executor, GracefulShutdownProperties properties) {
        this.executor = executor;
        this.properties = properties;
    }

    @WriteOperation
    public Map<String, Object> offline() {
        if (!verifyToken()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "forbidden");
            error.put("message", "missing or invalid " + TOKEN_HEADER + " header");
            return error;
        }
        log.info("收到主动下线请求（preStop），开始优雅下线编排");
        executor.shutdown();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "shutting-down");
        result.put("message", "graceful shutdown initiated, health marked DOWN, deregistered, waited for consumers");
        return result;
    }

    /**
     * 校验共享密钥：未配置 token 时放行并 WARN（提醒生产必配）；
     * 配置了则请求头必须精确匹配，不匹配置 403.
     */
    private boolean verifyToken() {
        String expected = properties.getEndpointToken();
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (expected == null || expected.isBlank()) {
            log.warn("me.cloud.shutdown.endpoint-token 未配置，/actuator/offline 处于未鉴权状态，生产环境必须配置");
            return true;
        }
        String actual = attrs != null ? attrs.getRequest().getHeader(TOKEN_HEADER) : null;
        if (expected.equals(actual)) {
            return true;
        }
        log.warn("主动下线请求密钥不匹配，已拒绝");
        if (attrs != null && attrs.getResponse() != null) {
            attrs.getResponse().setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
        return false;
    }
}
