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
}
