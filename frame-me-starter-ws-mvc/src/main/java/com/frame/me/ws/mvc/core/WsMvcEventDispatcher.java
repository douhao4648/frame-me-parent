package com.frame.me.ws.mvc.core;

import com.frame.me.event.MeApplicationEvent;
import com.frame.me.ws.mvc.config.WsMvcProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.time.Instant;

/**
 * 把本地 {@link MeApplicationEvent} 桥接到 WebSocket 广播通道.
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class WsMvcEventDispatcher {

    private final WsMvcSessionManager sessionManager;
    private final WsMvcProperties properties;

    @EventListener
    @Order(Integer.MAX_VALUE - 99)
    public void onApplicationEvent(MeApplicationEvent event) {
        String eventType = event.getEventType();
        WsMvcPayload payload = WsMvcPayload.builder()
                .eventType(eventType)
                .data(event.getPayload())
                .timestamp(Instant.now())
                .build();

        String targetId = event.getTargetId();
        if (targetId != null && !targetId.isBlank()) {
            if (!properties.isTargetedEnabled()) {
                log.debug("WebSocket targeted dispatch disabled, skip targetId={}", targetId);
                return;
            }
            int sent = sessionManager.pushToReceiver(targetId, payload);
            log.debug("WebSocket targeted push: type={}, targetId={}, sent={}", eventType, targetId, sent);
            return;
        }

        if (!properties.isBroadcastEnabled()) {
            return;
        }
        int sent = sessionManager.broadcast(eventType, payload);
        log.debug("WebSocket broadcast: type={}, sent={}", eventType, sent);
    }
}
