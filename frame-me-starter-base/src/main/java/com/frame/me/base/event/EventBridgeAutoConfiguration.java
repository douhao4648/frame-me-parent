package com.frame.me.base.event;

import com.frame.me.base.env.EnvironmentHelper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    private final Environment environment;

    public EventBridgeAutoConfiguration(EventBridgeProperties eventBridgeProperties,
                                        EnvironmentHelper environmentHelper,
                                        Environment environment) {
        this.eventBridgeProperties = eventBridgeProperties;
        this.environmentHelper = environmentHelper;
        this.environment = environment;
    }

    /**
     * 解析本机主机名：HOSTNAME 环境变量优先（k8s 中为 Pod 名，可读且稳定），
     * 缺失时 InetAddress 兜底；任何异常（DNS、SecurityManager）都返回 null 走 UUID 档，
     * 不允许因主机名解析导致启动失败.
     *
     * @return 主机名，解析失败返回 {@code null}
     */
    private static String resolveHostName() {
        String host = System.getenv("HOSTNAME");
        if (StringUtils.hasText(host)) {
            return host;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.debug("EventBridge instanceId 无法解析本机主机名，将回退为随机 UUID", e);
            return null;
        }
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

    /**
     * 若用户未显式配置 me.event-bridge.instance-id，则回退为 {@code <host>:<server.port>}；
     * 主机名或端口不可用（含 server.port=0 随机端口场景）时生成启动随机 UUID，
     * 保证实例级自过滤（{@link EventBridgeProperties.SelfFilter#INSTANCE}，默认）始终可用.
     *
     * <p>server.port=0 时本阶段读到的是字面值而非真实端口，同机多实例会撞成相同
     * {@code host:0} 互吞消息，必须降级 UUID。</p>
     */
    @PostConstruct
    public void applyInstanceIdDefault() {
        if (StringUtils.hasText(eventBridgeProperties.getInstanceId())) {
            return;
        }
        String host = resolveHostName();
        String port = environment.getProperty("server.port");
        if (StringUtils.hasText(host) && StringUtils.hasText(port) && !"0".equals(port)) {
            String derived = host + ":" + port;
            eventBridgeProperties.setInstanceId(derived);
            log.debug("EventBridge instanceId default to host:port: {}", derived);
            return;
        }
        String generated = UUID.randomUUID().toString();
        eventBridgeProperties.setInstanceId(generated);
        log.warn("EventBridge instanceId 未配置且无法从 HOSTNAME/server.port 推导，已生成启动随机 UUID {}；"
                + "建议显式配置 me.event-bridge.instance-id 或确保 HOSTNAME 环境变量与 server.port 可用", generated);
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
                                                   Map<String, IEventTransport> transports,
                                                   org.springframework.beans.factory.ObjectProvider<IEventErrorHandler> errorHandlerProvider) {
        return new EventBridgeListener(publisher, properties, normalizeTransports(transports),
                Optional.ofNullable(errorHandlerProvider.getIfAvailable()));
    }
}
