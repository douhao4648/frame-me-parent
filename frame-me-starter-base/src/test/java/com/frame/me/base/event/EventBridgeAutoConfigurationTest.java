package com.frame.me.base.event;

import com.frame.me.base.config.BaseAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EventBridgeAutoConfiguration} 测试.
 *
 * <p>用 {@link WebApplicationContextRunner} 而非 {@code ApplicationContextRunner}：
 * {@link BaseAutoConfiguration} 标注 {@code @ConditionalOnWebApplication(SERVLET)}，
 * 非 Web runner 下整体退避，{@code EnvironmentHelper} bean 不创建，
 * {@link EventBridgeAutoConfiguration} 构造器注入失败.</p>
 */
class EventBridgeAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(BaseAutoConfiguration.class, EventBridgeAutoConfiguration.class);

    @Test
    void shouldDefaultServiceNameToApplicationName() {
        contextRunner
                .withPropertyValues("spring.application.name=frame-me-tester")
                .run(context -> {
                    EventBridgeProperties properties = context.getBean(EventBridgeProperties.class);
                    assertThat(properties.getServiceName()).isEqualTo("frame-me-tester");
                });
    }

    @Test
    void shouldUseExplicitServiceNameWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "spring.application.name=frame-me-tester",
                        "me.event-bridge.service-name=custom-service")
                .run(context -> {
                    EventBridgeProperties properties = context.getBean(EventBridgeProperties.class);
                    assertThat(properties.getServiceName()).isEqualTo("custom-service");
                });
    }

    /**
     * 两项都未配置时生成实例唯一名：保证自过滤可用（否则自身事件回声后重复执行），
     * 且不同实例唯一名不同、不会互吞事件。
     */
    @Test
    void shouldGenerateUniqueServiceNameWhenNoAppNameConfigured() {
        contextRunner.run(context -> {
            EventBridgeProperties properties = context.getBean(EventBridgeProperties.class);
            assertThat(properties.getServiceName()).matches("unknown-[0-9a-f]{8}");
        });
    }

    @Test
    void shouldUseExplicitInstanceIdWhenConfigured() {
        contextRunner
                .withPropertyValues("me.event-bridge.instance-id=pod-1:8080")
                .run(context -> {
                    EventBridgeProperties properties = context.getBean(EventBridgeProperties.class);
                    assertThat(properties.getInstanceId()).isEqualTo("pod-1:8080");
                });
    }

    /**
     * 未配置时回退 host:server.port（host 部分不断言具体值：CI 无 HOSTNAME 时走 InetAddress，行为不定）.
     */
    @Test
    void shouldDeriveInstanceIdFromHostAndPort() {
        contextRunner
                .withPropertyValues("server.port=18080")
                .run(context -> {
                    EventBridgeProperties properties = context.getBean(EventBridgeProperties.class);
                    assertThat(properties.getInstanceId()).endsWith(":18080");
                });
    }

    /**
     * 端口不可用（含 server.port=0 随机端口，此阶段读到的是字面值）时回退启动 UUID 全段.
     */
    @Test
    void shouldFallbackToUuidWhenNoPortConfigured() {
        contextRunner.run(context -> {
            EventBridgeProperties properties = context.getBean(EventBridgeProperties.class);
            assertThat(properties.getInstanceId()).matches("[0-9a-f-]{36}");
        });
    }

    @Test
    void shouldFallbackToUuidWhenPortIsZero() {
        contextRunner
                .withPropertyValues("server.port=0")
                .run(context -> {
                    EventBridgeProperties properties = context.getBean(EventBridgeProperties.class);
                    assertThat(properties.getInstanceId()).matches("[0-9a-f-]{36}");
                });
    }

    /**
     * self-filter 绑定：默认 INSTANCE（实例级自过滤），可配 service 回旧语义.
     */
    @Test
    void shouldBindSelfFilterMode() {
        contextRunner.run(context -> {
            EventBridgeProperties properties = context.getBean(EventBridgeProperties.class);
            assertThat(properties.getSelfFilter()).isEqualTo(EventBridgeProperties.SelfFilter.INSTANCE);
        });
        contextRunner
                .withPropertyValues("me.event-bridge.self-filter=service")
                .run(context -> {
                    EventBridgeProperties properties = context.getBean(EventBridgeProperties.class);
                    assertThat(properties.getSelfFilter()).isEqualTo(EventBridgeProperties.SelfFilter.SERVICE);
                });
    }
}
