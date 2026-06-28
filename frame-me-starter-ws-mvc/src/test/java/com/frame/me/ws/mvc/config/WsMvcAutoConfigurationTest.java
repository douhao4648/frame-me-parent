package com.frame.me.ws.mvc.config;

import com.frame.me.ws.mvc.core.WsMvcEventDispatcher;
import com.frame.me.ws.mvc.core.WsMvcSessionManager;
import com.frame.me.ws.mvc.handler.MeWsMvcHandler;
import com.frame.me.ws.mvc.service.WsMvcPushService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WsMvcAutoConfiguration} 条件装配测试.
 *
 * @author frame-me
 */
class WsMvcAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WsMvcAutoConfiguration.class));

    @Test
    void shouldAutoConfigureWhenWebAppAndEnabled() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(WsMvcSessionManager.class);
            assertThat(context).hasSingleBean(WsMvcEventDispatcher.class);
            assertThat(context).hasSingleBean(WsMvcPushService.class);
            assertThat(context).hasSingleBean(MeWsMvcHandler.class);
        });
    }

    @Test
    void shouldNotAutoConfigureWhenDisabled() {
        webRunner.withPropertyValues("me.ws.mvc.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(WsMvcAutoConfiguration.class));
    }

    @Test
    void shouldNotConfigureDispatcherWhenBroadcastDisabled() {
        webRunner.withPropertyValues("me.ws.mvc.broadcast-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(WsMvcSessionManager.class);
                    assertThat(context).doesNotHaveBean(WsMvcEventDispatcher.class);
                    assertThat(context).hasSingleBean(WsMvcPushService.class);
                });
    }

    @Test
    void shouldNotAutoConfigureInNonWebContext() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WsMvcAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(WsMvcSessionManager.class));
    }
}
