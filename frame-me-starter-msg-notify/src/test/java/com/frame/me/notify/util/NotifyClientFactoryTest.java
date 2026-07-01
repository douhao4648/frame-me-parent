package com.frame.me.notify.util;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NotifyClientFactory} 测试.
 */
class NotifyClientFactoryTest {

    @AfterEach
    void tearDown() {
        NotifyClientFactory.init(Map.of(), Map.of());
    }

    @Test
    void shouldGetRegisteredClient() {
        INotifyClient client = stubClient("alert", "email");
        NotifyClientFactory.init(Map.of("alert", client), Map.of());

        assertThat(NotifyClientFactory.getClient("alert")).isSameAs(client);
        assertThat(NotifyClientFactory.clientNames()).containsExactly("alert");
        assertThat(NotifyClientFactory.hasClient("alert")).isTrue();
    }

    @Test
    void shouldGetChannelDefaultClientName() {
        INotifyClient emailClient = stubClient("email", "email");
        INotifyClient webhookClient = stubClient("webhook", "webhook");
        NotifyClientFactory.init(
                Map.of("email", emailClient, "webhook", webhookClient),
                Map.of("email", "email", "webhook", "webhook"));

        assertThat(NotifyClientFactory.getDefaultClientName("email")).isEqualTo("email");
        assertThat(NotifyClientFactory.getDefaultClientName("webhook")).isEqualTo("webhook");
    }

    @Test
    void shouldThrowWhenChannelDefaultNotConfigured() {
        NotifyClientFactory.init(Map.of(), Map.of());

        assertThatThrownBy(() -> NotifyClientFactory.getDefaultClientName("email"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No default client configured for channel 'email'");
    }

    @Test
    void shouldThrowWhenClientNotFound() {
        NotifyClientFactory.init(Map.of(), Map.of());

        assertThatThrownBy(() -> NotifyClientFactory.getClient("missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void shouldReturnGlobalDefaultClientWhenConfigured() {
        INotifyClient emailClient = stubClient("email", "email");
        NotifyClientFactory.init(
                Map.of("email", emailClient),
                Map.of("email", "email"),
                "email");

        Optional<INotifyClient> globalDefault = NotifyClientFactory.getGlobalDefaultClient();
        assertThat(globalDefault).isPresent();
        assertThat(globalDefault.get()).isSameAs(emailClient);
        assertThat(NotifyClientFactory.hasGlobalDefault()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenGlobalDefaultNotConfigured() {
        INotifyClient emailClient = stubClient("email", "email");
        NotifyClientFactory.init(Map.of("email", emailClient), Map.of("email", "email"));

        assertThat(NotifyClientFactory.getGlobalDefaultClient()).isEmpty();
        assertThat(NotifyClientFactory.hasGlobalDefault()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenGlobalDefaultClientNotRegistered() {
        INotifyClient emailClient = stubClient("email", "email");
        NotifyClientFactory.init(
                Map.of("email", emailClient),
                Map.of("email", "email"),
                "webhook");

        assertThat(NotifyClientFactory.getGlobalDefaultClient()).isEmpty();
        assertThat(NotifyClientFactory.hasGlobalDefault()).isFalse();
    }

    private INotifyClient stubClient(String name, String channelType) {
        return new INotifyClient() {
            @Override
            public NotifyResult send(NotifyMessage message) {
                return NotifyResult.ok("ok");
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
