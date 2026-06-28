package com.frame.me.tester.event;

import com.frame.me.event.EventType;
import com.frame.me.event.MeApplicationEvent;
import org.springframework.stereotype.Component;

/**
 * 用户创建事件类型注册项.
 *
 * @author frame-me
 */
@Component
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
