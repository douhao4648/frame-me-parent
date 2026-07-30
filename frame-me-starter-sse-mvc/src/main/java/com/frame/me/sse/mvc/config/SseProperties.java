package com.frame.me.sse.mvc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 配置属性.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.sse")
public class SseProperties {

    /**
     * 是否启用 SSE，默认 true.
     */
    private boolean enabled = true;

    /**
     * SseEmitter 超时时间（毫秒），0 表示不超时，默认 0.
     */
    private long timeout = 0L;

    /**
     * 客户端重连间隔（毫秒），默认 3000.
     *
     * <p>订阅建立后立即向客户端发送 SSE {@code retry:} 指令，客户端断线后按此间隔重连.
     * 仅当客户端断线主动重连时生效；服务端探测死连接由 {@link #heartbeatInterval} 负责.</p>
     */
    private long retry = 3000L;

    /**
     * 心跳间隔（秒），默认 0 表示不发送心跳.
     *
     * <p>大于 0 时定时向所有存活 Emitter 发送 SSE comment（{@code :heartbeat\n\n}），
     * 保持代理/负载均衡连接活跃；发送失败即触发 Emitter 清理，避免半关闭连接滞留.
     * 需开启调度支持（默认开启）.</p>
     */
    private long heartbeatInterval = 0L;

    /**
     * 是否自动把 {@link com.frame.me.event.MeApplicationEvent} 广播到 SSE，默认 true.
     */
    private boolean broadcastEnabled = true;

    /**
     * 是否启用定向订阅，默认 true.
     */
    private boolean targetedEnabled = true;

    /**
     * 单服务实例最大并发 Emitter 数，0 表示无限制，默认 0.
     */
    private int maxEmitters = 0;

    /**
     * SSE 订阅接口基础路径，默认 {@code /api/sse}.
     * <p>
     * 必须以 {@code /} 开头，除根路径 {@code /} 外不能以 {@code /} 结尾。
     * 配置后，广播/定向订阅接口均会迁移到该路径下。
     */
    private String path = "/api/sse";
}
