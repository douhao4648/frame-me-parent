package com.frame.me.tester.event;

import com.frame.me.event.MeApplicationEvent;
import lombok.Getter;

/**
 * 用户创建事件.
 *
 * @author frame-me
 */
@Getter
public class UserCreatedEvent extends MeApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 创建用户创建事件.
     *
     * @param source  事件源
     * @param payload 用户创建负载
     */
    public UserCreatedEvent(Object source, UserCreatedPayload payload) {
        super(payload);
        this.source = source;
        this.payload = payload;
    }

    private final Object source;
    private final UserCreatedPayload payload;

    @Override
    public String getEventType() {
        return "user:created";
    }
}
