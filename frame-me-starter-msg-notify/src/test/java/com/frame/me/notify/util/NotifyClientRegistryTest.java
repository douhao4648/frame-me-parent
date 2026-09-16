package com.frame.me.notify.util;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotifyClientRegistryTest {

    @Test
    void shouldGetRegisteredAndDefaultClients() {
        INotifyClient email = stubClient("email", "email");
        INotifyClient webhook = stubClient("webhook", "webhook");
        NotifyClientRegistry registry = new NotifyClientRegistry(
                Map.of("email", email, "webhook", webhook),
                Map.of("email", "email", "webhook", "webhook"),
                "email");

        assertThat(registry.getClient("email")).isSameAs(email);
        assertThat(registry.email()).isSameAs(email);
        assertThat(registry.webhook()).isSameAs(webhook);
        assertThat(registry.getGlobalDefaultClient()).contains(email);
        assertThat(registry.clientNames()).containsExactlyInAnyOrder("email", "webhook");
        assertThat(registry.hasClient("email")).isTrue();
        assertThat(registry.hasGlobalDefault()).isTrue();
    }

    @Test
    void shouldGetNamedChannelClients() {
        INotifyClient email = stubClient("alert", "email");
        INotifyClient webhook = stubClient("ops", "webhook");
        INotifyClient sms = stubClient("marketing", "sms");
        NotifyClientRegistry registry = new NotifyClientRegistry(
                Map.of("email:alert", email, "webhook:ops", webhook, "sms:marketing", sms),
                Map.of(), null);

        assertThat(registry.email("alert")).isSameAs(email);
        assertThat(registry.webhook("ops")).isSameAs(webhook);
        assertThat(registry.sms("marketing")).isSameAs(sms);
    }

    @Test
    void shouldRejectMissingOrWrongChannelClient() {
        INotifyClient email = stubClient("email", "email");
        NotifyClientRegistry registry = new NotifyClientRegistry(
                Map.of("email", email, "webhook:wrong", email),
                Map.of("email", "email"), null);

        assertThatThrownBy(registry::webhook)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook");
        assertThatThrownBy(() -> registry.webhook("wrong"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a webhook client");
        assertThatThrownBy(() -> registry.getClient("missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void sendShouldUseGlobalDefaultOrReturnFailure() {
        INotifyClient email = stubClient("email", "email");
        NotifyClientRegistry configured = new NotifyClientRegistry(
                Map.of("email", email), Map.of("email", "email"), "email");
        NotifyClientRegistry empty = new NotifyClientRegistry(Map.of(), Map.of(), null);

        assertThat(configured.send("title", "content", "receiver@example.com").isSuccess()).isTrue();
        NotifyResult failure = empty.send("title", "content", "receiver@example.com");
        assertThat(failure.isSuccess()).isFalse();
        assertThat(failure.getCode()).isEqualTo("NO_GLOBAL_DEFAULT");
    }

    @Test
    void registriesAreIsolated() {
        INotifyClient firstClient = stubClient("first", "email");
        INotifyClient secondClient = stubClient("second", "email");
        NotifyClientRegistry first = new NotifyClientRegistry(
                Map.of("email", firstClient), Map.of("email", "email"), "email");
        NotifyClientRegistry second = new NotifyClientRegistry(
                Map.of("email", secondClient), Map.of("email", "email"), "email");

        assertThat(first.email()).isSameAs(firstClient);
        assertThat(second.email()).isSameAs(secondClient);
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
