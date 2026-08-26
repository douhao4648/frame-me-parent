package com.frame.me.base.event;

/**
 * 跨服务事件处理错误处理器 SPI.
 *
 * <p>{@link EventBridgeMessage} 在本地反序列化或 {@code @EventListener} 消费抛异常时，
 * 默认行为仅 {@code log.error} 后丢弃消息（跨服务事件「至少一次」语义下可能丢）.
 * 业务方可实现本接口并注册为 Bean，按需补充：
 * <ul>
 *   <li>重试：对瞬时故障（如 Redis 抖动）重试消费;</li>
 *   <li>死信：持久化失败消息到死信表 / 队列，供人工介入或补偿;</li>
 *   <li>告警：上报监控系统.</li>
 * </ul>
 *
 * <p>未注册本接口 Bean 时保持原行为（仅记日志），不强制依赖.
 * <b>幂等约定</b>：跨服务事件「至少一次」语义要求消费方幂等，payload 应携带业务幂等键.
 *
 * @author frame-me
 */
public interface IEventErrorHandler {

    /**
     * 处理跨服务事件消费失败.
     *
     * @param message   桥接消息（含 type / payload / sourceService）
     * @param exception 消费过程中抛出的异常
     */
    void handleError(EventBridgeMessage message, Exception exception);
}
