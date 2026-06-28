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

    /** 是否启用，默认 true. */
    private boolean enabled = true;

    /** 是否自动广播 {@link com.frame.me.event.MeApplicationEvent}，默认 true. */
    private boolean broadcastEnabled = true;

    /** 是否启用定向订阅，默认 true. */
    private boolean targetedEnabled = true;

    /** 单服务实例最大并发 session 数，0 无限制. */
    private int maxSessions = 0;

    /** 心跳间隔（秒），0 表示不发送心跳. */
    private int heartbeatInterval = 30;

    /** 握手允许的 Origins，空表示允许所有（生产环境建议显式配置）. */
    private List<String> allowedOrigins = Collections.emptyList();
}
