package com.frame.me.notify.config;

import com.frame.me.notify.util.NotifyClientFactory;
import com.frame.me.notify.util.NotifyUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NotifyProperties} 与默认客户端配置绑定测试.
 */
@SpringBootTest(classes = NotifyPropertiesTest.TestConfig.class)
@ImportAutoConfiguration(NotifyAutoConfiguration.class)
@TestPropertySource(properties = {
        "me.notify.enabled=true",
        "me.notify.global-default=email",
        "me.notify.email.host=smtp.example.com",
        "me.notify.email.port=587",
        "me.notify.email.username=default@example.com",
        "me.notify.email.password=default-pass",
        "me.notify.email.from=default@example.com",
        "me.notify.email.clients.alert.host=smtp.alert.com",
        "me.notify.email.clients.alert.port=465",
        "me.notify.email.clients.alert.username=alert@example.com",
        "me.notify.email.clients.alert.password=alert-pass",
        "me.notify.email.clients.alert.from=alert@example.com",
        "me.notify.webhook.url=https://hook.example.com/default",
        "me.notify.webhook.clients.ops.url=https://hook.example.com/ops",
        "me.notify.sms.url=https://sms.example.com/send",
        "me.notify.sms.app-key=app-key",
        "me.notify.sms.app-secret=app-secret",
        "me.notify.sms.sign-name=FrameMe",
        "me.notify.sms.clients.marketing.url=https://sms.example.com/marketing"
})
class NotifyPropertiesTest {

    @Test
    void shouldBindDefaultClientAndRegisterClients() {
        assertThat(NotifyClientFactory.hasClient("email")).isTrue();
        assertThat(NotifyClientFactory.hasClient("email:alert")).isTrue();
        assertThat(NotifyClientFactory.hasClient("webhook")).isTrue();
        assertThat(NotifyClientFactory.hasClient("webhook:ops")).isTrue();
        assertThat(NotifyClientFactory.hasClient("sms")).isTrue();
        assertThat(NotifyClientFactory.hasClient("sms:marketing")).isTrue();
        assertThat(NotifyClientFactory.clientNames())
                .containsExactlyInAnyOrder("email", "email:alert", "webhook", "webhook:ops", "sms", "sms:marketing");
    }

    @Test
    void shouldRegisterGlobalDefaultClient() {
        assertThat(NotifyClientFactory.hasGlobalDefault()).isTrue();
        assertThat(NotifyUtils.defaultClient())
                .isPresent()
                .hasValueSatisfying(client -> assertThat(client.getChannelType()).isEqualTo("email"));
    }

    @Configuration
    @EnableConfigurationProperties(NotifyProperties.class)
    static class TestConfig {
    }
}
