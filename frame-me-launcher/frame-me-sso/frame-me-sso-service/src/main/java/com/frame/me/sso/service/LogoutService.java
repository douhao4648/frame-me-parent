package com.frame.me.sso.service;

import cn.dev33.satoken.stp.StpUtil;
import com.frame.me.base.event.EventBridgePublisher;
import com.frame.me.sso.infrastructure.event.UserLogoutEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SSO 登出服务（踢人 + 发事件）.
 *
 * <p>执行 sa-token 注销会话 + 通过事件桥接发布 {@link UserLogoutEvent} 供下游订阅.
 * 事件经 {@link EventBridgePublisher} 先本地发布，再跨进程广播（Redis pub/sub），
 * 下游订阅 type={@value UserLogoutEvent#EVENT_TYPE} 清自己 sa-token session.</p>
 *
 * @author frame-me
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final EventBridgePublisher eventBridgePublisher;

    /**
     * 强制登出用户（踢人）.
     *
     * @param userId 用户 ID
     * @param appId  应用 ID（可选，null 表示踢所有应用会话）
     * @param reason 原因
     */
    public void logout(Long userId, String appId, String reason) {
        try {
            if (appId != null && !appId.isBlank()) {
                // 按 app 踢：deviceType=appId，只清该 userId 在该 appId 的会话
                StpUtil.logout(userId, appId);
            } else {
                // 踢所有 app 会话
                StpUtil.logout(userId);
            }
            log.info("强制登出用户: userId={}, appId={}, reason={}", userId, appId, reason);
        } catch (Exception e) {
            log.warn("强制登出失败: userId={}", userId, e);
            throw e;
        }
        // 事件桥接发布：本地 + 跨进程广播（档3：下游订阅清 session）
        eventBridgePublisher.publish(new UserLogoutEvent(this, userId, appId, reason));
    }
}
