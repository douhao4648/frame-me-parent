package com.frame.me.ws.mvc.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocket 端点默认路径集成测试.
 *
 * @author frame-me
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WsMvcEndpointIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void shouldConnectDefaultPathAndRespondPong() throws Exception {
        CountDownLatch openLatch = new CountDownLatch(1);
        CountDownLatch pongLatch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        TextWebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                openLatch.countDown();
                session.sendMessage(new TextMessage("ping"));
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                received.set(message.getPayload());
                pongLatch.countDown();
            }
        };

        URI uri = URI.create("ws://localhost:" + port + "/api/ws?type=broadcast&eventType=user:created");
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler, uri.toString()).get(3, TimeUnit.SECONDS);

        try {
            assertThat(openLatch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(pongLatch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get()).isEqualTo("pong");
        } finally {
            session.close();
        }
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
