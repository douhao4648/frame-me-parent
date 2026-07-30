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
import java.util.UUID;

/**
 * 事件桥接自动配置.
 *
 * <p>仅在 {@code me.event-bridge.enabled=true}（默认）时装配，提供发布器和监听器 Bean。
 * 具体的 {@link IEventTransport} 实现由各自 starter（如 Redis、MQ）提供。</p>
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
     * 若用户未显式配置 me.event-bridge.service-name，则回退到 spring.application.name；
     * 两者都缺失时生成实例唯一名，保证自过滤可用。
     *
     * <p>服务名为 {@code "unknown"} 时 {@link EventBridgeListener} 的自过滤会被跳过，
     * 本服务发出的事件经 transport 回声后会在本地重复执行一次；
     * 若改为固定名互判，多个未命名服务又会互吞事件。因此生成 {@code unknown-<uuid>} 唯一名：
     * 自身回声带自身唯一名可被过滤，不同实例唯一名不同也不会互吞。</p>
     */
    @PostConstruct
    public void applyServiceNameDefault() {
        if ("unknown".equals(eventBridgeProperties.getServiceName())) {
            String appName = environmentHelper.getApplicationName();
            if (StringUtils.hasText(appName)) {
                eventBridgeProperties.setServiceName(appName);
                log.debug("EventBridge serviceName default to spring.application.name: {}", appName);
            } else {
                String generated = "unknown-" + UUID.randomUUID().toString().substring(0, 8);
                eventBridgeProperties.setServiceName(generated);
                log.warn("EventBridge serviceName 未配置且 spring.application.name 为空，已生成临时唯一名 {}；"
                        + "建议显式配置 spring.application.name 或 me.event-bridge.service-name", generated);
            }
        }
    }

    @Bean
    public EventBridgePublisher eventBridgePublisher(ApplicationEventPublisher publisher,
                                                     EventBridgeProperties properties,
                                                     Map<String, IEventTransport> transports) {
        return new EventBridgePublisher(publisher, properties, normalizeTransports(transports));
    }

    @Bean
    public EventBridgeListener eventBridgeListener(ApplicationEventPublisher publisher,
                                                   EventBridgeProperties properties,
                                                   Map<String, IEventTransport> transports) {
        return new EventBridgeListener(publisher, properties, normalizeTransports(transports));
    }

    /**
     * 规范化 transport 名称.
     *
     * <p>例如 {@code redisEventTransport} 同时支持以 {@code redis} 作为 key 查找，
     * 让配置中可以使用简洁名称。注意 Bean 名按实现类命名，仍以 {@code EventTransport} 结尾，
     * 而非接口 {@link IEventTransport} 的 {@code I} 前缀。</p>
     *
     * @param transports 原始 transport Bean Map
     * @return 规范化后的 Map
     */
    private static Map<String, IEventTransport> normalizeTransports(Map<String, IEventTransport> transports) {
        Map<String, IEventTransport> result = new HashMap<>(transports);
        transports.forEach((name, transport) -> {
            if (name.endsWith("EventTransport")) {
                String shortName = name.substring(0, name.length() - "EventTransport".length());
                result.putIfAbsent(shortName, transport);
            }
        });
        return result;
    }
}
