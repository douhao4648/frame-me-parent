package com.frame.me.notify.webhook;

import com.frame.me.notify.config.WebhookChannelProperties;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WebhookNotifyClient} 测试.
 */
class WebhookNotifyClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger statusCode = new AtomicInteger(200);
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedSignature = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", (HttpHandler) exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedSignature.set(exchange.getRequestHeaders().getFirst("X-Webhook-Signature"));
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode.get(), response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        statusCode.set(200);
        receivedBody.set(null);
        receivedSignature.set(null);
    }

    @Test
    void shouldSendWebhookSuccessfully() {
        WebhookNotifyClient client = createClient(baseUrl, null);

        NotifyResult result = client.send(NotifyMessage.builder()
                .title("告警")
                .content("服务异常")
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponseId()).isEqualTo("200");
        assertThat(receivedBody.get()).contains("\"title\":\"告警\"");
        assertThat(receivedBody.get()).contains("\"content\":\"服务异常\"");
    }

    @Test
    void shouldFailWhenResponseError() {
        statusCode.set(500);
        WebhookNotifyClient client = createClient(baseUrl, null);

        NotifyResult result = client.send(NotifyMessage.builder()
                .title("告警")
                .content("服务异常")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("WEBHOOK_RESPONSE_ERROR");
    }

    @Test
    void shouldFailWhenServerUnavailable() {
        WebhookNotifyClient client = createClient("http://127.0.0.1:1/hook", null);

        NotifyResult result = client.send(NotifyMessage.builder()
                .title("告警")
                .content("服务异常")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("WEBHOOK_SEND_ERROR");
    }

    @Test
    void shouldSendSignatureWhenSecretConfigured() {
        WebhookNotifyClient client = createClient(baseUrl, "secret-key");

        client.send(NotifyMessage.builder()
                .title("告警")
                .content("服务异常")
                .build());

        assertThat(receivedSignature.get()).isNotNull();
    }

    private WebhookNotifyClient createClient(String url, String secret) {
        WebhookChannelProperties properties = new WebhookChannelProperties();
        properties.setUrl(url);
        properties.setSecret(secret);
        properties.setTimeout(2000);
        return new WebhookNotifyClient("test", properties, RestClient.builder());
    }
}
