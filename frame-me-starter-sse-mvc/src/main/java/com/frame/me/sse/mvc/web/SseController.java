package com.frame.me.sse.mvc.web;

import com.frame.me.sse.mvc.SseConstant;
import com.frame.me.sse.mvc.config.SseProperties;
import com.frame.me.sse.mvc.core.SseEmitterManager;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 订阅端点.
 *
 * @author frame-me
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterManager emitterManager;
    private final SseProperties properties;

    /**
     * 广播订阅：按事件类型接收所有该类型事件.
     *
     * @param eventType 事件类型
     * @param response  HTTP 响应
     * @return SseEmitter
     */
    @GetMapping(value = SseConstant.BROADCAST_PATH + "{eventType}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeBroadcast(@PathVariable String eventType, HttpServletResponse response) {
        prepareResponse(response);
        if (!properties.isBroadcastEnabled()) {
            throw new IllegalStateException("SSE broadcast is disabled");
        }
        log.debug("SSE broadcast subscribe: eventType={}", eventType);
        return emitterManager.registerBroadcast(eventType);
    }

    /**
     * 定向订阅：注册接收者标识，接收专属推送.
     *
     * @param receiverId 接收者标识
     * @param response   HTTP 响应
     * @return SseEmitter
     */
    @GetMapping(value = SseConstant.TARGETED_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeTargeted(@RequestParam("receiverId") String receiverId, HttpServletResponse response) {
        prepareResponse(response);
        if (!properties.isTargetedEnabled()) {
            throw new IllegalStateException("SSE targeted push is disabled");
        }
        log.debug("SSE targeted subscribe: receiverId={}", receiverId);
        return emitterManager.registerTargeted(receiverId);
    }

    private void prepareResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
    }
}
