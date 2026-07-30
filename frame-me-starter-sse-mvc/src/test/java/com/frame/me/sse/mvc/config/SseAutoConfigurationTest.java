package com.frame.me.sse.mvc.config;

import com.frame.me.sse.mvc.core.SseEmitterManager;
import com.frame.me.sse.mvc.core.SseEventDispatcher;
import com.frame.me.sse.mvc.service.SsePushService;
import com.frame.me.sse.mvc.web.SseController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SseAutoConfiguration} 条件装配测试.
 *
 * @author frame-me
 */
class SseAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SseAutoConfiguration.class));

    @Test
    void shouldAutoConfigureWhenWebAppAndEnabled() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(SseEmitterManager.class);
            assertThat(context).hasSingleBean(SseEventDispatcher.class);
            assertThat(context).hasSingleBean(SsePushService.class);
            assertThat(context).hasSingleBean(SseController.class);
        });
    }

    @Test
    void shouldNotAutoConfigureWhenDisabled() {
        webRunner.withPropertyValues("me.sse.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SseAutoConfiguration.class));
    }

    /**
     * 仅关广播、定向仍开启时 Dispatcher 必须装配（定向事件推送依赖它），
     * 广播分支由 Dispatcher 内部运行时开关拦截.
     */
    @Test
    void shouldKeepDispatcherWhenOnlyBroadcastDisabled() {
        webRunner.withPropertyValues("me.sse.broadcast-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(SseEmitterManager.class);
                    assertThat(context).hasSingleBean(SseEventDispatcher.class);
                    assertThat(context).hasSingleBean(SsePushService.class);
                });
    }

    @Test
    void shouldKeepDispatcherWhenOnlyTargetedDisabled() {
        webRunner.withPropertyValues("me.sse.targeted-enabled=false")
                .run(context -> assertThat(context).hasSingleBean(SseEventDispatcher.class));
    }

    @Test
    void shouldNotConfigureDispatcherWhenBothDisabled() {
        webRunner.withPropertyValues("me.sse.broadcast-enabled=false", "me.sse.targeted-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(SseEmitterManager.class);
                    assertThat(context).doesNotHaveBean(SseEventDispatcher.class);
                    assertThat(context).hasSingleBean(SsePushService.class);
                });
    }

    @Test
    void shouldNotAutoConfigureInNonWebContext() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(SseEmitterManager.class));
    }

    /**
     * heartbeat-interval > 0 时装配心跳任务与调度支持；为 0（默认）时不装配，避免引入全局调度.
     */
    @Test
    void shouldConfigureHeartbeatWhenIntervalPositive() {
        webRunner.withPropertyValues("me.sse.heartbeat-interval=30")
                .run(context -> assertThat(context).hasSingleBean(SseHeartbeatTask.class));
    }

    @Test
    void shouldNotConfigureHeartbeatByDefault() {
        webRunner.run(context -> assertThat(context).doesNotHaveBean(SseHeartbeatTask.class));
    }
}
