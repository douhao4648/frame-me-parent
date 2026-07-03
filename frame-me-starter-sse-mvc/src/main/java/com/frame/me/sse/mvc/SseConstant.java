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
}
