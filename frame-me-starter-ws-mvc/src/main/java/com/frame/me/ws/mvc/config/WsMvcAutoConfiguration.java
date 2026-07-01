package com.frame.me.ws.mvc.config;

import com.frame.me.ws.mvc.WsMvcConstant;
import com.frame.me.ws.mvc.core.WsMvcEventDispatcher;
import com.frame.me.ws.mvc.core.WsMvcSessionManager;
import com.frame.me.ws.mvc.handler.MeWsMvcHandler;
import com.frame.me.ws.mvc.service.WsMvcPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * Servlet 原生 WebSocket 自动配置.
 *
 * <p>本 starter 采用原生 {@link WebSocketHandler} 自己维护 {@link org.springframework.web.socket.WebSocketSession}，
 * 适合“服务端主动推送 + 简单客户端回包”场景。</p>
 *
 * <p>后续可扩展方向（与本模块路径不冲突）：
 * <ul>
 *   <li><b>WebFlux 原生 WebSocket</b>：新增 {@code frame-me-starter-ws-webflux}，
 *       走 {@code org.springframework.web.reactive.socket.WebSocketHandler} + {@code WebSocketHandlerAdapter}，
 *       路径可规划为 {@code /me/ws/reactive} 或复用 {@code /me/ws} 但仅对 reactive web 应用生效。</li>
 *   <li><b>Servlet STOMP</b>：新增 {@code frame-me-starter-ws-stomp}，
 *       走 {@code @EnableWebSocketMessageBroker}，使用 {@code SimpMessagingTemplate}，
 *       路径建议 {@code /me/ws/stomp}，提供主题/队列/ACK 等能力。</li>
 *   <li><b>RSocket</b>：新增 {@code frame-me-starter-rsocket}，
 *       作为 WebFlux 生态下的反应式双向消息协议，支持 fire-and-forget / request-response / stream。</li>
 * </ul>
 * </p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebSocketHandler.class)
@ConditionalOnProperty(prefix = "me.ws.mvc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WsMvcProperties.class)
public class WsMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WsMvcSessionManager wsMvcSessionManager(WsMvcProperties properties) {
        log.info("WsMvcSessionManager initialized, maxSessions={}, heartbeat={}", properties.getMaxSessions(), properties.getHeartbeatInterval());
        return new WsMvcSessionManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "me.ws.mvc", name = "broadcast-enabled", havingValue = "true", matchIfMissing = true)
    public WsMvcEventDispatcher wsMvcEventDispatcher(WsMvcSessionManager manager, WsMvcProperties properties) {
        return new WsMvcEventDispatcher(manager, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WsMvcPushService wsMvcPushService(WsMvcSessionManager manager) {
        return new WsMvcPushService(manager);
    }

    @Bean
    @ConditionalOnMissingBean
    public MeWsMvcHandler meWsMvcHandler(WsMvcSessionManager manager, WsMvcProperties properties) {
        return new MeWsMvcHandler(manager, properties);
    }

    /**
     * 调度与心跳任务配置.
     *
     * <p>单独拆分为内部配置类，受 {@code me.ws.mvc.scheduling-enabled} 控制；关闭时不会启用
     * {@link EnableScheduling @Scheduled}，也不会创建心跳任务。</p>
     */
    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "me.ws.mvc", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
    @RequiredArgsConstructor
    public static class WsMvcSchedulingConfiguration {

        private final WsMvcSessionManager manager;
        private final WsMvcProperties properties;

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnExpression("${me.ws.mvc.heartbeat-interval:0} > 0")
        public WsMvcHeartbeatTask wsMvcHeartbeatTask() {
            log.info("WsMvcHeartbeatTask initialized, heartbeatInterval={}", properties.getHeartbeatInterval());
            return new WsMvcHeartbeatTask(manager, properties);
        }
    }

    /**
     * WebSocket 端点注册.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @RequiredArgsConstructor
    public static class WsMvcHandlerRegistrationConfiguration implements WebSocketConfigurer {

        private final MeWsMvcHandler handler;
        private final WsMvcProperties properties;

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            var registration = registry.addHandler(handler, WsMvcConstant.WS_ENDPOINT);
            List<String> origins = properties.getAllowedOrigins();
            if (origins != null && !origins.isEmpty()) {
                registration.setAllowedOrigins(origins.toArray(new String[0]));
            } else {
                registration.setAllowedOrigins("*");
            }
            log.info("WebSocket MVC endpoint registered: {}", WsMvcConstant.WS_ENDPOINT);
        }
    }
}
