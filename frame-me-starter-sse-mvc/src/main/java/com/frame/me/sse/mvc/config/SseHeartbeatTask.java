package com.frame.me.sse.mvc.config;

import com.frame.me.sse.mvc.core.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * SSE 心跳任务.
 *
 * <p>定时向所有存活 Emitter 发送 SSE comment 保活，探测半关闭连接并在失败时清理 Emitter.
 * 仅当 {@code me.sse.heartbeat-interval > 0} 且调度启用时装配.</p>
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class SseHeartbeatTask {

    private final SseEmitterManager emitterManager;

    /**
     * 定时发送心跳.
     */
    @Scheduled(fixedRateString = "#{${me.sse.heartbeat-interval:0} * 1000}")
    public void heartbeat() {
        if (emitterManager.activeEmitterCount() == 0) {
            return;
        }
        int alive = emitterManager.heartbeat();
        log.debug("SSE heartbeat sent to {} emitters", alive);
    }
}
