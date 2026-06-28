package com.frame.me.sse.mvc.core;

import com.frame.me.event.EventClientPermit;
import com.frame.me.event.MeApplicationEvent;
import com.frame.me.sse.mvc.config.SseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.time.Instant;

/**
 * 把本地 {@link MeApplicationEvent} 桥接到 SSE 广播通道.
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class SseEventDispatcher {

    private final SseEmitterManager emitterManager;
    private final SseProperties properties;

    @EventListener
    @Order(Integer.MAX_VALUE - 100)
    public void onApplicationEvent(MeApplicationEvent event) {
        if (!event.getClass().isAnnotationPresent(EventClientPermit.class)) {
            log.debug("SSE skip event without @EventClientPermit: type={}", event.getEventType());
            return;
        }

        String eventType = event.getEventType();
        SsePayload payload = SsePayload.builder()
                .eventType(eventType)
                .data(event.getPayload())
                .timestamp(Instant.now())
                .build();

        String targetId = event.getTargetId();
        if (targetId != null && !targetId.isBlank()) {
            if (!properties.isTargetedEnabled()) {
                log.debug("SSE targeted dispatch disabled, skip targetId={}", targetId);
                return;
            }
            int sent = emitterManager.pushToReceiver(targetId, payload);
            log.debug("SSE targeted push: type={}, targetId={}, sent={}", eventType, targetId, sent);
            return;
        }

        if (!properties.isBroadcastEnabled()) {
            return;
        }
        int sent = emitterManager.broadcast(eventType, payload);
        log.debug("SSE broadcast: type={}, sent={}", eventType, sent);
    }
}
