package com.frame.me.cloud.shutdown;

import com.frame.me.cloud.config.CloudAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 优雅下线自动装配测试.
 *
 * <p>用 {@link ApplicationContextRunner}：本模块自动装配全部 web 栈无关
 * （offline 端点 token 走 {@code @Selector} 路径段），无需 Servlet mock 环境.</p>
 *
 * @author frame-me
 */
class CloudAutoConfigurationShutdownTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
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
