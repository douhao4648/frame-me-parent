package com.frame.me.sse.mvc.config;

import com.frame.me.sse.mvc.core.SseEmitterManager;
import com.frame.me.sse.mvc.core.SseEventDispatcher;
import com.frame.me.sse.mvc.service.SsePushService;
import com.frame.me.sse.mvc.web.SseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    public SseEmitterManager sseEmitterManager(SseProperties properties) {
        log.info("SseEmitterManager initialized, timeout={}, maxEmitters={}",
                properties.getTimeout(), properties.getMaxEmitters());
        return new SseEmitterManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "me.sse", name = "broadcast-enabled", havingValue = "true", matchIfMissing = true)
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
}
