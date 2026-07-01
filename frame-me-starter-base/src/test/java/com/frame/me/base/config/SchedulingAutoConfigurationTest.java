package com.frame.me.base.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SchedulingAutoConfiguration} 测试.
 */
class SchedulingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingAutoConfiguration.class);

    @Test
    void shouldCreateTaskSchedulerWithDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
            SchedulingProperties properties = context.getBean(SchedulingProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getPoolSize()).isEqualTo(4);
            assertThat(properties.getThreadNamePrefix()).isEqualTo("me-scheduling-");
        });
    }

    @Test
    void shouldApplyCustomProperties() {
        contextRunner
                .withPropertyValues(
                        "me.scheduling.pool-size=8",
                        "me.scheduling.thread-name-prefix=custom-scheduling-",
                        "me.scheduling.remove-on-cancel-policy=true",
                        "me.scheduling.await-termination-seconds=10")
                .run(context -> {
                    SchedulingProperties properties = context.getBean(SchedulingProperties.class);
                    assertThat(properties.getPoolSize()).isEqualTo(8);
                    assertThat(properties.getThreadNamePrefix()).isEqualTo("custom-scheduling-");
                    assertThat(properties.isRemoveOnCancelPolicy()).isTrue();
                    assertThat(properties.getAwaitTerminationSeconds()).isEqualTo(10);
                    assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
                });
    }

    @Test
    void shouldNotCreateTaskSchedulerWhenDisabled() {
        contextRunner
                .withPropertyValues("me.scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ThreadPoolTaskScheduler.class));
    }

    @Test
    void shouldNotOverrideCustomTaskScheduler() {
        new ApplicationContextRunner()
                .withUserConfiguration(CustomTaskSchedulerConfiguration.class, SchedulingAutoConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskScheduler.class);
                    assertThat(context.getBean(TaskScheduler.class))
                            .isInstanceOf(CustomTaskSchedulerConfiguration.CustomScheduler.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTaskSchedulerConfiguration {

        @Bean
        TaskScheduler customTaskScheduler() {
            return new CustomScheduler();
        }

        static class CustomScheduler extends ThreadPoolTaskScheduler {
            private static final long serialVersionUID = 1L;
        }
    }
}
