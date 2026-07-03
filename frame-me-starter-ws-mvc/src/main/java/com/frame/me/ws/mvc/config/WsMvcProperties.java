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
     * 是否自动广播 {@link com.frame.me.event.MeApplicationEvent}，默认 true.
     */
    private boolean broadcastEnabled = true;

    /**
     * 是否启用定向订阅，默认 true.
     */
    private boolean targetedEnabled = true;

    /**
     * 单服务实例最大并发 session 数，0 表示无限制，默认 0.
     */
    private int maxSessions = 0;

    /**
     * 是否启用调度支持（含心跳任务），默认 true.
     */
    private boolean schedulingEnabled = true;

    /**
     * 心跳间隔（秒），0 表示不发送心跳，默认 30.
     */
    private int heartbeatInterval = 30;

    /**
     * 握手允许的 Origins，空表示允许所有（生产环境建议显式配置），默认空列表.
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
