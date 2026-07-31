package com.frame.me.sse.mvc;

/**
 * SSE 常量.
 *
 * @author frame-me
 */
public final class SseConstant {

    private SseConstant() {
    }

    /** 订阅路径后缀. */
    public static final String SUBSCRIBE_PATH = "/subscribe";

    /** 默认 SSE 超时（毫秒），0 表示不超时. */
    public static final long DEFAULT_TIMEOUT = 0L;

    /** 默认客户端重试间隔（毫秒）. */
    public static final long DEFAULT_RETRY = 3000L;

    /** eventType / receiverId 安全字符白名单（字母数字、冒号、下划线、短横、点）。 */
    public static final String SAFE_ID_PATTERN = "^[A-Za-z0-9_:\\-]+$";

    /** eventType / receiverId 最大长度，防恶意超长 key 撑爆 ConcurrentHashMap。 */
    public static final int MAX_ID_LENGTH = 128;
}
