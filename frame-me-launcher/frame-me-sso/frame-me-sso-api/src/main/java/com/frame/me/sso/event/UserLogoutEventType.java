package com.frame.me.sso.event;

import com.frame.me.event.IEventType;
import com.frame.me.event.MeApplicationEvent;

/**
 * 用户登出事件类型注册项.
 *
 * <p>由 {@link UserLogoutEventConfiguration} 显式注册，
 * 避免消费方因组件扫描路径不一致导致事件类型无法被 {@code EventBridgeListener} 收集。</p>
 *
 * @author frame-me
 */
public class UserLogoutEventType implements IEventType<UserLogoutEvent.Payload> {

    @Override
    public String type() {
        return UserLogoutEvent.EVENT_TYPE;
    }

    @Override
    public Class<UserLogoutEvent.Payload> payloadClass() {
        return UserLogoutEvent.Payload.class;
    }

    @Override
    public MeApplicationEvent toLocalEvent(UserLogoutEvent.Payload payload, String source, String sourceInstanceId) {
        UserLogoutEvent event = new UserLogoutEvent(source, payload.getUserId(), payload.getAppId(), payload.getReason());
        // 还原原始登出时间，不用重建时的当前时间
        event.setLogoutTime(payload.getLogoutTime());
        return event;
    }
}
