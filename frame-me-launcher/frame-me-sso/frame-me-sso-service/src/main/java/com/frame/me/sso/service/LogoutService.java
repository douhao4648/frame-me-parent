package com.frame.me.sso.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.frame.me.base.event.EventBridgePublisher;
import com.frame.me.sso.infrastructure.event.UserLogoutEvent;
import com.frame.me.sso.infrastructure.satoken.SsoTokenUtils;
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
    public int logoutByApp(String appId, String reason) {
        int kicked = 0;
        try {
            // 应用 token 会话（client_credentials 颁发，loginId="app:"+appId）
            StpUtil.logout(SsoTokenUtils.appLoginId(appId));
            kicked++;
            // 遍历全部 sa-token 会话，踢 deviceType=appId 的用户 token。
            // searchSessionId("", 0, -1) 全量扫描——应用数/会话数有限，可接受
            String prefix = SaManager.getConfig().getTokenName() + ":login:session:";
            for (String sessionId : StpUtil.searchSessionId("", 0, -1, false)) {
                String loginId = sessionId.substring(prefix.length());
                SaSession session = StpUtil.getSessionByLoginId(loginId, false);
                if (session == null) {
                    continue;
                }
                // terminalListCopy：logoutByTokenValue 会改 terminalList，直接遍历 Vector 抛 CME
                for (SaTerminalInfo terminal : session.terminalListCopy()) {
                    if (appId.equals(terminal.getDeviceType())) {
                        StpUtil.logoutByTokenValue(terminal.getTokenValue());
                        kicked++;
                    }
                }
            }
            log.info("按应用强制登出: appId={}, kicked={}, reason={}", appId, kicked, reason);
        } catch (Exception e) {
            log.warn("按应用强制登出失败: appId={}", appId, e);
            throw e;
        }
        // userId=null：整应用踢人，payload 语义见 UserLogoutEvent
        eventBridgePublisher.publish(new UserLogoutEvent(this, null, appId, reason));
        return kicked;
    }
}
