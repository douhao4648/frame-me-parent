package com.frame.me.sse.mvc.web;

import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.sse.mvc.SseConstant;
import com.frame.me.sse.mvc.config.SseProperties;
import com.frame.me.sse.mvc.core.SseEmitterManager;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 订阅端点.
 *
 * <p><b>鉴权由业务方叠加</b>：本 starter 是纯传输层，不依赖认证模块（避免传输层耦合认证）。
 * 生产部署应通过 {@code frame-me-starter-auth-sa-token} 的 {@code me.auth.sa-token.rules}
 * 或 {@code frame-me-starter-auth-rbac} 的 {@code me.auth.permission.rules} 配置路径规则保护
 * {@code /api/sse/subscribe/**}，或自定义 {@code HandlerInterceptor} 做登录校验。</p>
 *
 * <p>DoS 防护：单实例最大并发 Emitter 数受 {@code me.sse.max-emitters}（默认 1000）限制，
 * 超限返回 429；{@code eventType}/{@code receiverId} 做长度与字符白名单校验，非法返回 400.</p>
 *
 * @author frame-me
 */
@Tag(name = "SSE 订阅", description = "广播订阅、定向订阅")
@Slf4j
@RestController
@RequestMapping("${me.sse.path:/api/sse}")
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
    @GetMapping(value = SseConstant.SUBSCRIBE_PATH + "/{eventType}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeBroadcast(@PathVariable String eventType, HttpServletResponse response) {
        prepareResponse(response);
        if (!properties.isBroadcastEnabled()) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "SSE broadcast is disabled");
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
    @GetMapping(value = SseConstant.SUBSCRIBE_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeTargeted(@RequestParam("receiverId") String receiverId, HttpServletResponse response) {
        prepareResponse(response);
        if (!properties.isTargetedEnabled()) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "SSE targeted push is disabled");
        }
        log.debug("SSE targeted subscribe: receiverId={}", receiverId);
        return emitterManager.registerTargeted(receiverId);
    }

    private void prepareResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
    }
}
