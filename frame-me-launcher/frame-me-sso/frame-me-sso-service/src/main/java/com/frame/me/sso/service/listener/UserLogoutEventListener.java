package com.frame.me.sso.service.listener;

import com.alibaba.fastjson2.JSON;
import com.frame.me.sso.event.UserLogoutEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 用户登出事件本地监听器.
 *
 * <p>本实例踢人（本地发布）与其他实例广播的踢人事件（桥接重发布）都会到达；
 * 演示打印 payload（userId/appId/logoutTime/reason），
 * 不序列化整个 event——source 是 LogoutService bean，序列化会连带 Spring bean 图。</p>
 *
 * @author frame-me
 */
@Slf4j
@Component
public class UserLogoutEventListener {

    @EventListener
    public void onUserLogout(UserLogoutEvent event) {
        log.info("sso 登出事件: {}", JSON.toJSONString(event.getPayload()));
    }

}
