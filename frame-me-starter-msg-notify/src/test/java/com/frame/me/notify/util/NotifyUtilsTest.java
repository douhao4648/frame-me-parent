package com.frame.me.notify.util;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NotifyUtils} 测试.
 */
class NotifyUtilsTest {

    @AfterEach
    void tearDown() {
        NotifyClientFactory.init(Map.of(), Map.of());
    }

    @Test
    void emailShouldReturnDefaultClient() {
        INotifyClient defaultEmail = stubClient("email", "email");
        NotifyClientFactory.init(Map.of("email", defaultEmail), Map.of("email", "email"));

        assertThat(NotifyUtils.email()).isSameAs(defaultEmail);
    }

    @Test
    void emailShouldReturnNamedClient() {
        INotifyClient alert = stubClient("alert", "email");
        NotifyClientFactory.init(Map.of("email:alert", alert), Map.of("email", "email"));

        assertThat(NotifyUtils.email("alert")).isSameAs(alert);
    }

    @Test
    void webhookShouldReturnDefaultClient() {
        INotifyClient defaultWebhook = stubClient("webhook", "webhook");
        NotifyClientFactory.init(Map.of("webhook", defaultWebhook), Map.of("webhook", "webhook"));

        assertThat(NotifyUtils.webhook()).isSameAs(defaultWebhook);
    }

    @Test
    void webhookShouldReturnNamedClient() {
        INotifyClient ops = stubClient("ops", "webhook");
        NotifyClientFactory.init(Map.of("webhook:ops", ops), Map.of("webhook", "webhook"));

        assertThat(NotifyUtils.webhook("ops")).isSameAs(ops);
    }

    @Test
    void webhookShouldFailWhenOnlyEmailConfigured() {
        INotifyClient defaultEmail = stubClient("email", "email");
        NotifyClientFactory.init(Map.of("email", defaultEmail), Map.of("email", "email"));

        assertThatThrownBy(() -> NotifyUtils.webhook())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook");
    }

    @Test
    void smsShouldReturnDefaultClient() {
        INotifyClient defaultSms = stubClient("sms", "sms");
        NotifyClientFactory.init(Map.of("sms", defaultSms), Map.of("sms", "sms"));

        assertThat(NotifyUtils.sms()).isSameAs(defaultSms);
    }

    @Test
    void smsShouldReturnNamedClient() {
        INotifyClient marketing = stubClient("marketing", "sms");
        NotifyClientFactory.init(Map.of("sms:marketing", marketing), Map.of("sms", "sms"));

        assertThat(NotifyUtils.sms("marketing")).isSameAs(marketing);
    }

    @Test
    void sendShouldUseGlobalDefaultClient() {
        INotifyClient emailClient = stubClient("email", "email");
        NotifyClientFactory.init(
                Map.of("email", emailClient),
                Map.of("email", "email"),
                "email");

        NotifyResult result = NotifyUtils.send("title", "content", "receiver@example.com");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponseId()).isEqualTo("sent-by-email");
    }

    @Test
    void sendShouldReturnFailWhenNoGlobalDefault() {
        NotifyClientFactory.init(Map.of(), Map.of());

        NotifyResult result = NotifyUtils.send("title", "content", "receiver@example.com");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("NO_GLOBAL_DEFAULT");
    }

    @Test
    void defaultClientShouldReturnGlobalDefault() {
        INotifyClient emailClient = stubClient("email", "email");
        NotifyClientFactory.init(
                Map.of("email", emailClient),
                Map.of("email", "email"),
                "email");

        assertThat(NotifyUtils.defaultClient()).isPresent();
        assertThat(NotifyUtils.defaultClient().get()).isSameAs(emailClient);
    }

    private INotifyClient stubClient(String name, String channelType) {
        return new INotifyClient() {
            @Override
            public NotifyResult send(NotifyMessage message) {
                return NotifyResult.ok("sent-by-" + channelType);
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getChannelType() {
                return channelType;
            }
        };
    }
}
