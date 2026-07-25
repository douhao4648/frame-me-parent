package com.frame.me.base.config;

import com.frame.me.base.notify.INotifySender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    @Test
    void shouldSendExceptionNotifyWithoutStacktraceByDefault() throws Exception {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setExceptionNotifyEnabled(true);
        properties.setExceptionNotifyReceivers(List.of("ops@example.com"));

        INotifySender sender = mock(INotifySender.class);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return true;
        }).when(sender).send(anyString(), anyString(), anyList());
        ObjectProvider<INotifySender> senderProvider = new ObjectProvider<>() {
            @Override
            public INotifySender getIfAvailable() {
                return sender;
            }
        };

        SchedulingAutoConfiguration configuration = new SchedulingAutoConfiguration();
        ThreadPoolTaskScheduler scheduler = configuration.taskScheduler(properties, senderProvider);
        scheduler.schedule(() -> {
            throw new RuntimeException("scheduling error");
        }, Instant.now());

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("调度任务执行异常"), contentCaptor.capture(), anyList());
        assertThat(contentCaptor.getValue())
                .contains("异常：" + RuntimeException.class.getName())
                .contains("消息：scheduling error")
                .doesNotContain("堆栈：");
    }

    @Test
    void shouldIncludeStacktraceWhenConfigured() throws Exception {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setExceptionNotifyEnabled(true);
        properties.setExceptionIncludeStacktrace(true);
        properties.setExceptionNotifyReceivers(List.of("ops@example.com"));

        INotifySender sender = mock(INotifySender.class);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return true;
        }).when(sender).send(anyString(), anyString(), anyList());
        ObjectProvider<INotifySender> senderProvider = new ObjectProvider<>() {
            @Override
            public INotifySender getIfAvailable() {
                return sender;
            }
        };

        SchedulingAutoConfiguration configuration = new SchedulingAutoConfiguration();
        ThreadPoolTaskScheduler scheduler = configuration.taskScheduler(properties, senderProvider);
        scheduler.schedule(() -> {
            throw new RuntimeException("scheduling error");
        }, Instant.now());

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("调度任务执行异常"), contentCaptor.capture(), anyList());
        assertThat(contentCaptor.getValue())
                .contains("堆栈：")
                .contains("at ");
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
