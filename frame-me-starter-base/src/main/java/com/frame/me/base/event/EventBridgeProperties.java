package com.frame.me.base.event;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.ConcurrentHashMap;
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
     * 当前服务名，用于追踪事件来源与自过滤.
     *
     * <p>默认 {@code "unknown"}；启动时由 {@code EventBridgeAutoConfiguration} 依次回退为
     * {@code spring.application.name}、随机唯一名（{@code unknown-<uuid>}），
     * 保证自过滤始终可用，不会被未配置拖垮。</p>
     */
    private String serviceName = "unknown";

    /**
     * 当前实例标识（JVM 进程级），用于实例级自过滤.
     *
     * <p>默认 {@code null}；启动时由 {@code EventBridgeAutoConfiguration} 依次回退为
     * {@code <host>:<server.port>}、启动随机 UUID（全段），保证实例级自过滤始终可用。
     * 显式配置时必须保证实例唯一，多实例配相同值会互吞消息。</p>
     */
    private String instanceId;

    /**
     * 自身消息过滤模式，默认 {@link SelfFilter#INSTANCE}.
     */
    private SelfFilter selfFilter = SelfFilter.INSTANCE;

    /**
     * 自身消息过滤模式.
     */
    enum SelfFilter {
        /**
         * 仅丢弃本 JVM 实例发出的回声消息（默认），同服务名其他实例的消息放行.
         */
        INSTANCE,
        /**
         * 丢弃同服务名的所有消息（旧语义，同服务多实例互收不到）.
         */
        SERVICE
    }

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
    private Map<String, String> transports = new ConcurrentHashMap<>();

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
