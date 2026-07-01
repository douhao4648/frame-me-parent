package com.frame.me.notify.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 通知发送结果.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyResult {

    /**
     * 是否成功.
     */
    private boolean success;

    /**
     * 结果码.
     */
    private String code;

    /**
     * 结果消息.
     */
    private String message;

    /**
     * 发送时间.
     */
    private Instant sendTime;

    /**
     * 响应数据（如邮件 message-id）.
     */
    private String responseId;

    public static NotifyResult ok(String responseId) {
        return NotifyResult.builder()
                .success(true)
                .code("200")
                .message("success")
                .responseId(responseId)
                .sendTime(Instant.now())
                .build();
    }

    public static NotifyResult fail(String code, String message) {
        return NotifyResult.builder()
                .success(false)
                .code(code)
                .message(message)
                .sendTime(Instant.now())
                .build();
    }
}
