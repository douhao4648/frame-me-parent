package com.frame.me.ws.mvc.core;

import com.frame.me.ws.mvc.config.WsMvcProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * {@link WsMvcSessionManager} 单元测试.
 *
 * @author frame-me
 */
class WsMvcSessionManagerTest {

    private WsMvcSessionManager manager;
    private WsMvcProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WsMvcProperties();
        manager = new WsMvcSessionManager(properties);
    }

    @Test
    void shouldRegisterBroadcastSession() {
        WebSocketSession session = mockSession("s1");
        manager.registerBroadcast(session, "user:created");

        assertThat(manager.broadcastChannelCount()).isEqualTo(1);
        assertThat(manager.activeSessionCount()).isEqualTo(1);
    }

    @Test
    void shouldRegisterTargetedSession() {
        WebSocketSession session = mockSession("s1");
        manager.registerTargeted(session, "user:123");

        assertThat(manager.targetedReceiverCount()).isEqualTo(1);
        assertThat(manager.activeSessionCount()).isEqualTo(1);
    }

    @Test
    void shouldBroadcastToMultipleSubscribers() throws IOException {
        WebSocketSession s1 = mockSession("s1");
        WebSocketSession s2 = mockSession("s2");
        manager.registerBroadcast(s1, "user:created");
        manager.registerBroadcast(s2, "user:created");

        int sent = manager.broadcast("user:created", WsMvcPayload.of("user:created", "hello"));

        assertThat(sent).isEqualTo(2);
        verify(s1, times(1)).sendMessage(any(TextMessage.class));
        verify(s2, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldPushToTargetedReceiver() throws IOException {
        WebSocketSession s1 = mockSession("s1");
        WebSocketSession s2 = mockSession("s2");
        manager.registerTargeted(s1, "user:123");
        manager.registerTargeted(s2, "user:123");

        int sent = manager.pushToReceiver("user:123", WsMvcPayload.of("message", "hi", "user:123"));

        assertThat(sent).isEqualTo(2);
    }

    @Test
    void shouldRemoveSessionOnSendFailure() throws IOException {
        WebSocketSession session = mockSession("s1");
        doThrow(new IOException("closed")).when(session).sendMessage(any(TextMessage.class));
        manager.registerBroadcast(session, "user:created");

        int sent = manager.broadcast("user:created", WsMvcPayload.of("user:created", "hello"));

        assertThat(sent).isEqualTo(0);
        assertThat(manager.activeSessionCount()).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroWhenNoSubscribers() {
        int sent = manager.broadcast("nonexistent", WsMvcPayload.of("x", "y"));
        assertThat(sent).isEqualTo(0);
    }

    @Test
    void shouldEnforceMaxSessions() {
        properties.setMaxSessions(2);
        WsMvcSessionManager limited = new WsMvcSessionManager(properties);

        limited.registerBroadcast(mockSession("s1"), "a");
        limited.registerBroadcast(mockSession("s2"), "b");

        assertThatThrownBy(() -> limited.registerBroadcast(mockSession("s3"), "c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("limit reached");
    }

    /**
     * 注册后持有的必须是装饰 session：并发发送经 ConcurrentWebSocketSessionDecorator 串行化.
     */
    @Test
    void shouldWrapSessionWithConcurrentDecorator() {
        manager.registerBroadcast(mockSession("s1"), "user:created");

        assertThat(manager.findSession("s1")).isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
        assertThat(manager.getAllSessions()).allMatch(s -> s instanceof ConcurrentWebSocketSessionDecorator);
    }

    @Test
    void shouldReturnNullForUnknownSessionId() {
        assertThat(manager.findSession("unknown")).isNull();
    }

    /**
     * handler 关闭/异常回调拿到的是原始 session，按 id 匹配同样能移除注册.
     */
    @Test
    void shouldRemoveSessionByRawSession() {
        WebSocketSession raw = mockSession("s1");
        manager.registerBroadcast(raw, "user:created");

        manager.removeSession(raw);

        assertThat(manager.activeSessionCount()).isEqualTo(0);
        assertThat(manager.broadcastChannelCount()).isEqualTo(0);
        assertThat(manager.findSession("s1")).isNull();
    }

    /**
     * 多线程经装饰 session 并发发送：底层 session 同一时刻最多一个发送在执行.
     */
    @Test
    void shouldSerializeConcurrentSends() throws Exception {
        WebSocketSession raw = mockSession("s1");
        AtomicInteger inFlight = new AtomicInteger();
        AtomicBoolean interleaved = new AtomicBoolean();
        doAnswer(invocation -> {
            if (inFlight.incrementAndGet() > 1) {
                interleaved.set(true);
            }
            try {
                Thread.sleep(1);
            } finally {
                inFlight.decrementAndGet();
            }
            return null;
        }).when(raw).sendMessage(any(TextMessage.class));
        manager.registerBroadcast(raw, "user:created");
        WebSocketSession decorated = manager.findSession("s1");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < 20; j++) {
                    decorated.sendMessage(new TextMessage("x"));
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(interleaved).isFalse();
    }

    /**
     * 移除 session 与并发注册新 session 竞态：compute 原子清理保证新 session 不丢.
     *
     * <p>旧实现 remove-then-removeKey 两步之间，并发注册拿到被清空的空 Set，
     * add 后又被 removeKey 误删；compute 把两步收敛到原子段，新 session 保留.</p>
     */
    @Test
    void shouldNotLoseConcurrentlyRegisteredSessionOnRemove() throws Exception {
        WebSocketSession s1 = mockSession("s1");
        WebSocketSession s2 = mockSession("s2");
        manager.registerBroadcast(s1, "user:created");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> removeFuture = pool.submit(() -> {
            start.await();
            manager.removeSession(s1);
            return null;
        });
        Future<?> registerFuture = pool.submit(() -> {
            start.await();
            manager.registerBroadcast(s2, "user:created");
            return null;
        });
        start.countDown();
        removeFuture.get();
        registerFuture.get();
        pool.shutdown();

        // s1 移除后 s2 应仍可广播命中；若竞态致 s2 丢失则 sent=0
        int sent = manager.broadcast("user:created", WsMvcPayload.of("user:created", "hi"));
        assertThat(sent).isEqualTo(1);
    }

    private WebSocketSession mockSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
