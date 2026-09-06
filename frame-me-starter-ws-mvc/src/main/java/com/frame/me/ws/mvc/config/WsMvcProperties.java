package com.frame.me.ws.mvc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

/**
 * WebSocket MVC 配置属性.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.ws.mvc")
public class WsMvcProperties {

    /**
     * 是否启用 WebSocket MVC，默认 true.
     */
    private boolean enabled = true;

    /**
     * 是否自动广播 {@link com.frame.me.event.AbstractMeApplicationEvent}，默认 true.
     */
    private boolean broadcastEnabled = true;

    /**
     * 是否启用定向订阅，默认 true.
     */
    private boolean targetedEnabled = true;

    /**
     * 单服务实例最大并发 session 数，默认 1000.
     *
     * <p>每个 WebSocket 长连接占用内存与发送线程资源，无上限时
     * 任意客户端可建数万连接打满内存/线程（DoS）。默认 1000 兜底，
     * 超限拒绝新连接（CloseStatus.TRY_AGAIN_LATER），业务方可按实例规格调大.</p>
     */
    private int maxSessions = 1000;

    /**
     * 是否启用调度支持（含心跳任务），默认 true.
     */
    private boolean schedulingEnabled = true;

    /**
     * 心跳间隔（秒），0 表示不发送心跳，默认 30.
     */
    private int heartbeatInterval = 30;

    /**
     * 单个 session 发送消息的最长耗时（毫秒），超时后该 session 被关闭，默认 10000.
     * <p>
     * 对应 {@code ConcurrentWebSocketSessionDecorator} 的 sendTimeLimit，防止慢客户端阻塞发送线程。
     */
    private int sendTimeLimit = 10_000;

    /**
     * 单个 session 的发送缓冲上限（字节），超过后该 session 被关闭，默认 65536.
     * <p>
     * 对应 {@code ConcurrentWebSocketSessionDecorator} 的 bufferSizeLimit，防止慢客户端积压消息撑爆内存。
     */
    private int bufferSizeLimit = 65_536;

    /**
     * 握手允许的 Origins，默认空列表.
     *
     * <p><b>空时不放宽跨域</b>：不调用 {@code setAllowedOrigins("*")}，仅允许同源连接，
     * 防止跨站 WebSocket 劫持（CSWSH）。生产环境如需跨域访问，必须显式配置可信 Origin 白名单
     * （如 {@code ["https://app.example.com"]}）——配置 {@code ["*"]} 等于全开放，仅限开发环境.
     * 鉴权由业务方通过 auth 路径规则或 {@code HandshakeInterceptor} 叠加.</p>
     */
    private List<String> allowedOrigins = Collections.emptyList();

    /**
     * WebSocket 端点路径，默认 {@code /api/ws}.
     * <p>
     * 必须以 {@code /} 开头，除根路径 {@code /} 外不能以 {@code /} 结尾。
     * 配置后，广播/定向订阅接口均会迁移到该路径下。
     */
    private String path = "/api/ws";
}
