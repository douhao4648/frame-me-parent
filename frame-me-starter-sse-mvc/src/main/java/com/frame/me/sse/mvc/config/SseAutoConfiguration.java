package com.frame.me.sse.mvc.config;

import com.frame.me.sse.mvc.core.SseEmitterManager;
import com.frame.me.sse.mvc.core.SseEventDispatcher;
import com.frame.me.sse.mvc.service.SsePushService;
import com.frame.me.sse.mvc.web.SseController;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 自动配置.
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SseEmitter.class)
@ConditionalOnProperty(prefix = "me.sse", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SseProperties.class)
public class SseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SseEmitterManager sseEmitterManager(SseProperties properties,
                                              org.springframework.beans.factory.ObjectProvider<com.frame.me.base.event.IReceiverIdAuthorizer> authorizerProvider) {
        log.info("SseEmitterManager initialized, timeout={}, maxEmitters={}, receiverIdAuthorizer={}",
                properties.getTimeout(), properties.getMaxEmitters(),
                authorizerProvider.getIfAvailable() != null);
        return new SseEmitterManager(properties, java.util.Optional.ofNullable(authorizerProvider.getIfAvailable()));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("${me.sse.broadcast-enabled:true} or ${me.sse.targeted-enabled:true}")
    public SseEventDispatcher sseEventDispatcher(SseEmitterManager manager, SseProperties properties) {
        return new SseEventDispatcher(manager, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SsePushService ssePushService(SseEmitterManager manager) {
        return new SsePushService(manager);
    }

    @Bean
    @ConditionalOnMissingBean
    public SseController sseController(SseEmitterManager manager, SseProperties properties) {
        return new SseController(manager, properties);
    }

    /**
     * 调度与心跳任务配置.
     *
     * <p>受 {@code me.sse.heartbeat-interval} 控制：大于 0 时启用 {@link EnableScheduling @EnableScheduling}
     * 并创建 {@link SseHeartbeatTask}；为 0（默认）时不加载，避免对无心跳需求的应用引入全局调度.</p>
     */
    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnExpression("${me.sse.heartbeat-interval:0} > 0")
    @RequiredArgsConstructor
    public static class SseSchedulingConfiguration {

        private final SseEmitterManager emitterManager;

        @Bean
        @ConditionalOnMissingBean
        public SseHeartbeatTask sseHeartbeatTask() {
            log.info("SseHeartbeatTask initialized");
            return new SseHeartbeatTask(emitterManager);
        }
    }
}
