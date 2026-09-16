package com.frame.me.base.event;

/**
 * 跨服务事件处理错误处理器 SPI.
 *
 * <p>{@link EventBridgeMessage} 在本地反序列化或 {@code @EventListener} 消费抛异常时，
 * 默认行为仅 {@code log.error} 后丢弃消息。当前 Redis Pub/Sub 不会重投，业务方可实现本接口
 * 独立补充：
 * <ul>
 *   <li>重试：对瞬时故障（如 Redis 抖动）重试消费;</li>
 *   <li>死信：持久化失败消息到死信表 / 队列，供人工介入或补偿;</li>
 *   <li>告警：上报监控系统.</li>
 * </ul>
 *
 * <p>未注册本接口 Bean 时保持原行为（仅记日志），不强制依赖。本 SPI 不参与 transport ACK/NACK，
 * 因而不能把 at-most-once 通道提升为 at-least-once。消费方仍应使用 {@code eventId} 或业务键幂等，
 * 以处理多实例广播副本、发布方重试及未来的可靠 transport。</p>
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
