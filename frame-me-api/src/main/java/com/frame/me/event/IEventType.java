package com.frame.me.event;

/**
 * 事件类型注册项.
 *
 * <p>将 {@code type} 字符串映射到具体的事件负载类，用于反序列化并还原为本地事件。</p>
 *
 * @param <T> 负载类型
 * @author frame-me
 */
public interface IEventType<T> {

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
     * 将负载转换为本地 {@link MeApplicationEvent}.
     *
     * @param payload          反序列化后的负载对象
     * @param source           原始来源服务名
     * @param sourceInstanceId 原始来源实例标识（JVM 进程级），供消费方做实例级判断
     * @return 本地事件实例
     */
    MeApplicationEvent toLocalEvent(T payload, String source, String sourceInstanceId);
}
