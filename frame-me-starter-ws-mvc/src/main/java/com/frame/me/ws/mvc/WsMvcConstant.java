package com.frame.me.ws.mvc;

/**
 * WebSocket MVC 常量.
 *
 * @author frame-me
 */
public class WsMvcConstant {

    private WsMvcConstant() {
    }

    /** 订阅类型字段. */
    public static final String FIELD_SUBSCRIBE_TYPE = "type";

    /** 事件类型字段. */
    public static final String FIELD_EVENT_TYPE = "eventType";

    /** 接收者标识字段. */
    public static final String FIELD_RECEIVER_ID = "receiverId";

    /** 广播订阅类型. */
    public static final String SUBSCRIBE_BROADCAST = "broadcast";

    /** 定向订阅类型. */
    public static final String SUBSCRIBE_TARGETED = "targeted";

    /** 默认心跳间隔（秒）. */
    public static final int DEFAULT_HEARTBEAT_INTERVAL = 30;
}
