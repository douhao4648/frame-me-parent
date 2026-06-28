package com.frame.me.event;

/**
 * 事件类型注册项.
 *
 * <p>将 {@code type} 字符串映射到具体的事件负载类，用于反序列化并还原为本地事件。</p>
 *
 * @param <T> 负载类型
 * @author frame-me
 */
public interface EventType<T> {

    /**
     * 事件类型标识.
     *
     * @return 类型字符串
     */
    String type();

    /**
     * 负载类型.
     *
     * @return 负载 Class
     */
    Class<T> payloadClass();

    /**
     * 将负载转换为本地 ApplicationEvent.
     *
     * @param payload 反序列化后的负载对象
     * @param source  原始来源标识
     * @return 本地事件实例
     */
    MeApplicationEvent toLocalEvent(T payload, String source);
}
