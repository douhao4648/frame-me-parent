package com.frame.me.notify.sms;

import com.frame.me.notify.config.SmsChannelProperties;
import com.frame.me.notify.model.NotifyMessage;
import com.frame.me.notify.model.NotifyResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SmsNotifyClient} 测试.
 */
class SmsNotifyClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger statusCode = new AtomicInteger(200);
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedSignature = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sms", (HttpHandler) exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedSignature.set(exchange.getRequestHeaders().getFirst("X-Sms-Signature"));
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode.get(), response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
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
    void shouldSendSmsSuccessfully() {
        SmsNotifyClient client = createClient(baseUrl, null);

        NotifyResult result = client.send(NotifyMessage.builder()
                .title("VERIFY_CODE")
                .content("123456")
                .receivers(List.of("13800138000"))
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResponseId()).isEqualTo("200");
        assertThat(receivedBody.get()).contains("\"templateCode\":\"VERIFY_CODE\"");
        assertThat(receivedBody.get()).contains("\"templateParam\":\"123456\"");
        assertThat(receivedBody.get()).contains("\"phones\":[\"13800138000\"]");
    }

    @Test
    void shouldFailWhenReceiversEmpty() {
        SmsNotifyClient client = createClient(baseUrl, null);

        NotifyResult result = client.send(NotifyMessage.builder()
                .title("VERIFY_CODE")
                .content("123456")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("SMS_RECEIVERS_MISSING");
    }

    @Test
    void shouldFailWhenResponseError() {
        statusCode.set(500);
        SmsNotifyClient client = createClient(baseUrl, null);

        NotifyResult result = client.send(NotifyMessage.builder()
                .title("VERIFY_CODE")
                .content("123456")
                .receivers(List.of("13800138000"))
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo("SMS_RESPONSE_ERROR");
    }

    @Test
    void shouldSendSignatureWhenSecretConfigured() {
        SmsNotifyClient client = createClient(baseUrl, "secret-key");

        client.send(NotifyMessage.builder()
                .title("VERIFY_CODE")
                .content("123456")
                .receivers(List.of("13800138000"))
                .build());

        assertThat(receivedSignature.get()).isNotNull();
    }

    private SmsNotifyClient createClient(String url, String secret) {
        SmsChannelProperties properties = new SmsChannelProperties();
        properties.setUrl(url);
        properties.setAppSecret(secret);
        properties.setSignName("FrameMe");
        properties.setTimeout(2000);
        return new SmsNotifyClient("test", properties, RestClient.builder());
    }
}
