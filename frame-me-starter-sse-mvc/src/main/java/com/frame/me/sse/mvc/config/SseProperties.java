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
     * 客户端重连间隔（毫秒），写入 retry 字段，默认 3000.
     */
    private long retry = 3000L;

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
