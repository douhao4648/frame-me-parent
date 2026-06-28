package com.frame.me.tester.event;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户创建事件处理器.
 *
 * @author frame-me
 */
@Slf4j
@Component
public class UserCreatedEventHandler {

    @Getter
    private final List<UserCreatedPayload> received = new ArrayList<>();

    /**
     * 处理用户创建事件.
     *
     * @param event 事件
     */
    @EventListener
    public void onUserCreated(UserCreatedEvent event) {
        log.info("UserCreatedEvent received: userId={}, username={}",
                event.getPayload().getUserId(), event.getPayload().getUsername());
        received.add(event.getPayload());
    }

    /**
     * 清空已接收事件.
     */
    public void clear() {
        received.clear();
    }
}
