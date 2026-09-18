package com.frame.me.base.event;

/**
 * 定向订阅 receiverId 授权 SPI.
 *
 * <p>SSE / WebSocket 定向订阅（{@code receiverId}）存在越权风险：客户端可声明任意
 * {@code receiverId} 接收本不属于该用户的事件。传输层 starter（sse-mvc / ws-mvc）
 * 不依赖认证模块，无法自行判定 {@code receiverId} 与当前登录身份的归属关系，
 * 因此提供本 SPI 由业务方按需实现绑定.
 *
 * <p>业务方实现本接口并注册为 Bean，在 {@link #authorize(String)} 中从
 * {@code AuthContext} / {@code StpUtil} 取当前登录用户，校验 {@code receiverId}
 * 是否归属该用户（如 receiverId 即用户 ID 时校验相等，或为用户有权订阅的业务实体）.
 * 校验失败返回 {@code false}，传输层拒绝订阅（400/CloseStatus.POLICY_VIOLATION）.
 *
 * <p>未注册本接口的 Bean 时，传输层对定向订阅 <strong>fail-closed 拒绝</strong>
 * （对象级越权防护不能默认放行）。确无授权需求的场景（receiverId 不含用户归属语义）
 * 须显式声明 {@link #permitAll()} Bean 放行——显式 opt-in 而非默认裸奔.
 *
 * @author frame-me
 */
public interface IReceiverIdAuthorizer {

    /**
     * 显式放行所有 {@code receiverId}（无对象级授权需求时的显式 opt-in）.
     *
     * <p>注意：等同关闭对象级越权防护，仅在 {@code receiverId} 不含敏感归属语义
     * （如纯业务主题、非用户私有通道）时使用；用户私有通道请实现归属校验.</p>
     */
    static IReceiverIdAuthorizer permitAll() {
        return receiverId -> true;
    }

    /**
     * 校验当前请求是否有权订阅指定 {@code receiverId} 的事件.
     *
     * <p>实现方应从当前请求上下文（如 {@code AuthContext.getUser()}、
     * {@code StpUtil.getLoginIdAsLong()}）取登录身份，与 {@code receiverId} 做归属校验.
     *
     * @param receiverId 订阅的目标接收者标识（已通过传输层的格式与长度校验）
     * @return {@code true} 允许订阅；{@code false} 拒绝（返回 400 / 关闭连接）
     */
    boolean authorize(String receiverId);
}
