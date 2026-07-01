package com.frame.me.notify.notify;

import com.frame.me.notify.api.INotifyClient;
import com.frame.me.notify.config.NotifyProperties;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;
import com.frame.me.notify.util.NotifyClientFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MsgNotifySender} 测试.
 */
class MsgNotifySenderTest {

    @AfterEach
    void tearDown() {
        NotifyClientFactory.init(Map.of(), Map.of());
    }

    @Test
    void sendShouldUseGlobalDefaultClient() {
        RecordingClient emailClient = new RecordingClient("email", "email");
        NotifyClientFactory.init(
                Map.of("email", emailClient),
                Map.of("email", "email"),
                "email");
        MsgNotifySender sender = new MsgNotifySender(new NotifyProperties());

        boolean result = sender.send("title", "content", List.of("receiver@example.com"));

        assertThat(result).isTrue();
        assertThat(emailClient.lastMessage.getTitle()).isEqualTo("title");
        assertThat(emailClient.lastMessage.getContent()).isEqualTo("content");
        assertThat(emailClient.lastMessage.getReceivers()).containsExactly("receiver@example.com");
    }

    @Test
    void sendShouldFallbackToGlobalReceivers() {
        RecordingClient emailClient = new RecordingClient("email", "email");
        NotifyClientFactory.init(
                Map.of("email", emailClient),
                Map.of("email", "email"),
                "email");
        NotifyProperties properties = new NotifyProperties();
        properties.setGlobalReceivers(List.of("admin@example.com", "ops@example.com"));
        MsgNotifySender sender = new MsgNotifySender(properties);

        boolean result = sender.send("title", "content", List.of());

        assertThat(result).isTrue();
        assertThat(emailClient.lastMessage.getReceivers())
                .containsExactly("admin@example.com", "ops@example.com");
    }

    @Test
    void sendShouldReturnFalseWhenNoGlobalDefault() {
        MsgNotifySender sender = new MsgNotifySender(new NotifyProperties());

        boolean result = sender.send("title", "content", List.of("receiver@example.com"));

        assertThat(result).isFalse();
    }

    @Test
    void sendChannelShouldUseChannelDefaultClient() {
        RecordingClient emailClient = new RecordingClient("email", "email");
        NotifyClientFactory.init(
                Map.of("email", emailClient),
                Map.of("email", "email"));
        MsgNotifySender sender = new MsgNotifySender(new NotifyProperties());

        boolean result = sender.sendChannel("email", "title", "content", List.of("receiver@example.com"));

        assertThat(result).isTrue();
        assertThat(emailClient.lastMessage.getTitle()).isEqualTo("title");
    }

    @Test
    void sendChannelShouldReturnFalseWhenChannelNotConfigured() {
        MsgNotifySender sender = new MsgNotifySender(new NotifyProperties());

        boolean result = sender.sendChannel("email", "title", "content", List.of("receiver@example.com"));

        assertThat(result).isFalse();
    }

    @Test
    void sendClientShouldUseNamedClient() {
        RecordingClient alertClient = new RecordingClient("alert", "email");
        NotifyClientFactory.init(
                Map.of("email:alert", alertClient),
                Map.of("email", "email"));
        MsgNotifySender sender = new MsgNotifySender(new NotifyProperties());

        boolean result = sender.sendClient("email:alert", "title", "content", List.of("receiver@example.com"));

        assertThat(result).isTrue();
        assertThat(alertClient.lastMessage.getTitle()).isEqualTo("title");
    }

    @Test
    void sendClientShouldReturnFalseWhenClientNotRegistered() {
        MsgNotifySender sender = new MsgNotifySender(new NotifyProperties());

        boolean result = sender.sendClient("email:alert", "title", "content", List.of("receiver@example.com"));

        assertThat(result).isFalse();
    }

    private static class RecordingClient implements INotifyClient {

        private final String name;
        private final String channelType;
        private NotifyMessage lastMessage;

        RecordingClient(String name, String channelType) {
            this.name = name;
            this.channelType = channelType;
        }

        @Override
        public NotifyResult send(NotifyMessage message) {
            this.lastMessage = message;
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
    }
}
