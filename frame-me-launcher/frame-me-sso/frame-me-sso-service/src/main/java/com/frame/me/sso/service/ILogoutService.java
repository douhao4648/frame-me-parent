package com.frame.me.sso.service;

/**
 * SSO 登出服务接口（踢人 + 发事件）.
 *
 * <p>执行 sa-token 注销会话 + 通过事件桥接发布 {@link com.frame.me.sso.event.UserLogoutEvent}
 * 供下游订阅。事件经 {@code EventBridgePublisher} 先本地发布，再跨进程广播（Redis pub/sub），
 * 下游订阅 type={@value com.frame.me.sso.event.UserLogoutEvent#EVENT_TYPE} 清自己 sa-token session.</p>
 *
 * @author frame-me
 */
public interface ILogoutService {

    /**
     * 强制登出用户（踢人）.
     *
     * @param userId 用户 ID
     * @param appId  应用 ID（可选，null 表示踢所有应用会话）
     * @param reason 原因
     */
    void logout(Long userId, String appId, String reason);

    /**
     * 按应用踢人：注销该 appId 下全部会话——client_credentials 应用 token（loginId="app:"+appId）
     * + 所有用户 token（deviceType=appId 的 terminal）.
     *
     * <p>事件 payload 的 userId 为 null（与应用 token 的"无用户维度"语义一致），
     * 下游订阅方应以 appId 清该应用全部本地 session.</p>
     *
     * @param appId  应用 ID
     * @param reason 原因
     * @return 注销的会话数（应用 token + 各用户 token）
     */
    int logoutByApp(String appId, String reason);
}
