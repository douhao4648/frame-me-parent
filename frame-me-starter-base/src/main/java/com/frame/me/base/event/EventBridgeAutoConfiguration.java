package com.frame.me.base.event;

import com.frame.me.base.env.EnvironmentHelper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 事件桥接自动配置.
 *
 * <p>仅在 {@code me.event-bridge.enabled=true}（默认）时装配，提供发布器和监听器 Bean。
 * 具体的 {@link EventTransport} 实现由各自 starter（如 Redis、MQ）提供。</p>
 *
 * @author frame-me
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "me.event-bridge", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EventBridgeProperties.class)
public class EventBridgeAutoConfiguration {

    private final EventBridgeProperties eventBridgeProperties;

    private final EnvironmentHelper environmentHelper;

    public EventBridgeAutoConfiguration(EventBridgeProperties eventBridgeProperties,
                                        EnvironmentHelper environmentHelper) {
        this.eventBridgeProperties = eventBridgeProperties;
        this.environmentHelper = environmentHelper;
    }

    /**
     * 若用户未显式配置 me.event-bridge.service-name，则回退到 spring.application.name。
     */
    @PostConstruct
    public void applyServiceNameDefault() {
        if ("unknown".equals(eventBridgeProperties.getServiceName())) {
            String appName = environmentHelper.getApplicationName();
            if (StringUtils.hasText(appName)) {
                eventBridgeProperties.setServiceName(appName);
                log.debug("EventBridge serviceName default to spring.application.name: {}", appName);
            }
        }
    }

    @Bean
    public EventBridgePublisher eventBridgePublisher(ApplicationEventPublisher publisher,
                                                     EventBridgeProperties properties,
                                                     Map<String, EventTransport> transports) {
        return new EventBridgePublisher(publisher, properties, normalizeTransports(transports));
    }

    @Bean
    public EventBridgeListener eventBridgeListener(ApplicationEventPublisher publisher,
                                                   EventBridgeProperties properties,
                                                   Map<String, EventTransport> transports) {
        return new EventBridgeListener(publisher, properties, normalizeTransports(transports));
    }

    /**
     * 规范化 transport 名称.
     *
     * <p>例如 {@code redisEventTransport} 同时支持以 {@code redis} 作为 key 查找，
     * 让配置中可以使用简洁名称。</p>
     *
     * @param transports 原始 transport Bean Map
     * @return 规范化后的 Map
     */
    private static Map<String, EventTransport> normalizeTransports(Map<String, EventTransport> transports) {
        Map<String, EventTransport> result = new HashMap<>(transports);
        transports.forEach((name, transport) -> {
            if (name.endsWith("EventTransport")) {
                String shortName = name.substring(0, name.length() - "EventTransport".length());
                result.putIfAbsent(shortName, transport);
            }
        });
        return result;
    }
}
