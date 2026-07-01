package com.frame.me.base.event;

import com.frame.me.base.config.BaseAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EventBridgeAutoConfiguration} 测试.
 */
class EventBridgeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
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
}
