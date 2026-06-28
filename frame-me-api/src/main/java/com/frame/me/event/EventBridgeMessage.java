package com.frame.me.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 跨服务事件桥接的通用消息包装.
 *
 * <p>通过传输通道广播时，所有具体事件被序列化为该包装类型。
 * 接收方根据 {@code type} 字段分发到对应的本地 {@link org.springframework.context.ApplicationEvent}。</p>
 *
 * @author frame-me
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventBridgeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件类型标识，如 {@code "user:created"}.
     */
    private String type;

    /**
     * 事件负载，JSON 字符串形式.
     */
    private String payload;

    /**
     * 来源服务名（用于追踪，可选）.
     */
    private String sourceService;

    /**
     * 发送时间戳.
     */
    private Instant timestamp;

    /**
     * 快速构造方法.
     *
     * @param type          事件类型
     * @param payload       负载 JSON
     * @param sourceService 来源服务名
     * @return 消息实例
     */
    public static EventBridgeMessage of(String type, String payload, String sourceService) {
        return new EventBridgeMessage(type, payload, sourceService, Instant.now());
    }
}
