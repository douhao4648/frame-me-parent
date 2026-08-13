package com.frame.me.sso.auth;

import com.frame.me.auth.spi.IAuthService;
import com.frame.me.sso.event.UserLogoutEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * SSO 踢人事件监听器（清下游本地会话）.
 *
 * <p>SSO 踢人时发布 {@link UserLogoutEvent}（type={@code sso:user-logout}），
 * 经事件桥接跨进程广播。下游收到后调 {@link IAuthService#logoutByUserId(Long)}
 * 清本地会话——走认证 SPI，sa-token 实现清 sa-token 会话，JWT 实现删 Refresh Token
 * （Access Token 自然过期后无法续期），两套认证实现通用。</p>
 *
 * <p>{@code userId=null}（按应用踢）时，单应用 RP 暂不处理按应用维度踢人；
 * 后续如有多应用场景再扩展。消费方需幂等（跨服务事件"至少一次"语义）。</p>
 *
 * @author frame-me
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoLogoutEventListener {

    private final IAuthService authService;

    @EventListener
    public void onUserLogout(UserLogoutEvent event) {
        Long userId = event.getUserId();
        if (userId == null) {
            log.info("收到 userId=null 的踢人事件（按应用踢），暂不处理: appId={}",
                    event.getAppId());
            return;
        }
        try {
            authService.logoutByUserId(userId);
            log.info("SSO 踢人已清本地会话: userId={}, reason={}",
                    userId, event.getReason());
        } catch (Exception e) {
            // 清会话失败不阻断事件链路（幂等，下次事件可补偿）
            log.warn("清本地会话失败: userId={}", userId, e);
        }
    }
}
