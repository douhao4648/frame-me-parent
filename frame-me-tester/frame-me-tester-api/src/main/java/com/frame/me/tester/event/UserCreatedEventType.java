package com.frame.me.tester.event;

import com.frame.me.event.EventType;
import com.frame.me.event.MeApplicationEvent;

/**
 * 用户创建事件类型注册项.
 *
 * <p>不再使用 {@code @Component}，改为由 {@link UserCreatedEventConfiguration} 显式注册，
 * 避免消费方因组件扫描路径不一致导致事件类型无法被 {@code EventBridgeListener} 收集。</p>
 *
 * @author frame-me
 */
public class UserCreatedEventType implements EventType<UserCreatedPayload> {

    @Override
    public String type() {
        return "user:created";
    }

    @Override
    public Class<UserCreatedPayload> payloadClass() {
        return UserCreatedPayload.class;
    }

    @Override
    public MeApplicationEvent toLocalEvent(UserCreatedPayload payload, String source) {
        return new UserCreatedEvent(source, payload);
    }
}
