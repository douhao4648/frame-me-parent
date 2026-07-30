package com.frame.me.op.audit.config;

import com.frame.me.base.event.EventBridgeProperties;
import com.frame.me.base.event.EventBridgePublisher;
import com.frame.me.op.audit.aspect.AuditLogAspect;
import com.frame.me.op.audit.listener.AuditLogLogger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link AuditAutoConfiguration} 条件装配测试.
 *
 * @author frame-me
 */
class AuditAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class));

    /**
     * 无 EventBridge bean（me.event-bridge.enabled=false 的场景）时审计模块必须
     * 正常装配：桥接是增强不是前提，EventBridgeProperties 由本配置兜底注册.
     */
    @Test
    void shouldStartWithoutEventBridgeBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AuditLogAspect.class);
            assertThat(context).hasSingleBean(AuditLogLogger.class);
            assertThat(context).hasSingleBean(EventBridgeProperties.class);
            assertThat(context).doesNotHaveBean(EventBridgePublisher.class);
        });
    }

    @Test
    void shouldStartWithEventBridgePublisherPresent() {
        runner.withBean(EventBridgePublisher.class, () -> mock(EventBridgePublisher.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditLogAspect.class);
                    assertThat(context).hasSingleBean(AuditLogLogger.class);
                });
    }

    @Test
    void shouldNotStartWhenAuditDisabled() {
        runner.withPropertyValues("me.audit.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AuditLogAspect.class));
    }
}
