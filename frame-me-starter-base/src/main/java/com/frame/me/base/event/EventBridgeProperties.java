package com.frame.me.base.event;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 事件桥接配置属性.
 *
 * <p>绑定前缀 {@code me.event-bridge}。</p>
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.event-bridge")
public class EventBridgeProperties {

    /**
     * 是否启用事件桥接，默认 true.
     */
    private boolean enabled = true;

    /**
     * 当前服务名，用于追踪事件来源.
     */
    private String serviceName = "unknown";

    /**
     * Redis Topic 前缀，默认 {@code "me:event:"}.
     */
    private String topicPrefix = "me:event:";

    /**
     * 默认传输通道名称，默认 {@code "redis"}.
     */
    private String defaultTransport = "redis";

    /**
     * 按事件类型指定传输通道，key 为 type，value 为 transport Bean 名称（去掉 EventTransport 后缀后的简称）。
     * 例如 {@code user:created -> redis}。
     */
    private Map<String, String> transports = new HashMap<>();

    /**
     * 获取指定事件类型应使用的 transport 名称.
     *
     * @param type 事件类型
     * @return transport 名称
     */
    public String resolveTransport(String type) {
        return transports.getOrDefault(type, defaultTransport);
    }
}
