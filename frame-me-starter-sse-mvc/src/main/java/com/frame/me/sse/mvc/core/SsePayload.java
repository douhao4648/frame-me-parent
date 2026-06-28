package com.frame.me.sse.mvc.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SSE 统一消息体.
 *
 * @author frame-me
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsePayload {

    /** 事件类型标识. */
    private String eventType;

    /** 业务负载. */
    private Object data;

    /** 接收者标识（广播时为空）. */
    private String receiverId;

    /** 消息时间戳. */
    private Instant timestamp;

    /** 来源服务名. */
    private String sourceService;

    public static SsePayload of(String eventType, Object data) {
        return SsePayload.builder()
                .eventType(eventType)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static SsePayload of(String eventType, Object data, String receiverId) {
        return SsePayload.builder()
                .eventType(eventType)
                .data(data)
                .receiverId(receiverId)
                .timestamp(Instant.now())
                .build();
    }
}
