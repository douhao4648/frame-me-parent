package com.frame.me.cloud.shutdown;

import com.frame.me.cloud.config.CloudAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 优雅下线自动装配测试.
 *
 * <p>用 {@link WebApplicationContextRunner}（cloud 依赖的 actuator 有
 * {@code @ConditionalOnWebApplication}，非 Web runner 会让相关 bean 退避）.</p>
 *
 * @author frame-me
 */
class CloudAutoConfigurationShutdownTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CloudAutoConfiguration.class));

    @Test
    void enabled_assemblesAllShutdownBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ShutdownReadyFlag.class);
            assertThat(context).hasSingleBean(GracefulShutdownExecutor.class);
            assertThat(context).hasSingleBean(GracefulShutdownEndpoint.class);
            assertThat(context).hasSingleBean(GracefulShutdownListener.class);
        });
    }

    @Test
    void disabled_noShutdownBeansAssembled() {
        runner.withPropertyValues("me.cloud.shutdown.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ShutdownReadyFlag.class);
                    assertThat(context).doesNotHaveBean(GracefulShutdownExecutor.class);
                    assertThat(context).doesNotHaveBean(GracefulShutdownEndpoint.class);
                });
    }

    @Test
    void endpointDisabled_noEndpointBean() {
        runner.withPropertyValues("me.cloud.shutdown.endpoint-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ShutdownReadyFlag.class);
                    assertThat(context).doesNotHaveBean(GracefulShutdownEndpoint.class);
                });
    }
}
