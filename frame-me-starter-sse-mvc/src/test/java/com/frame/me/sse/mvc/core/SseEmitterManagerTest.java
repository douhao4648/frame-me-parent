package com.frame.me.sse.mvc.core;

import com.frame.me.sse.mvc.config.SseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SseEmitterManager} 单元测试.
 *
 * @author frame-me
 */
class SseEmitterManagerTest {

    private SseEmitterManager manager;

    @BeforeEach
    void setUp() {
        SseProperties properties = new SseProperties();
        properties.setTimeout(60000L);
        manager = new SseEmitterManager(properties);
    }

    @Test
    void shouldRegisterBroadcastEmitter() {
        SseEmitter emitter = manager.registerBroadcast("user:created");
        assertThat(emitter).isNotNull();
        assertThat(manager.broadcastChannelCount()).isEqualTo(1);
        assertThat(manager.activeEmitterCount()).isEqualTo(1);
    }

    @Test
    void shouldRegisterTargetedEmitter() {
        SseEmitter emitter = manager.registerTargeted("user:123");
        assertThat(emitter).isNotNull();
        assertThat(manager.targetedReceiverCount()).isEqualTo(1);
        assertThat(manager.activeEmitterCount()).isEqualTo(1);
    }

    @Test
    void shouldRemoveEmitterAfterSendFailure() {
        SseEmitter emitter = manager.registerBroadcast("user:created");
        emitter.complete();

        // 对已完成的 Emitter 发送会失败并触发清理
        manager.broadcast("user:created", SsePayload.of("user:created", "hello"));

        assertThat(manager.broadcastChannelCount()).isEqualTo(0);
        assertThat(manager.activeEmitterCount()).isEqualTo(0);
    }

    @Test
    void shouldBroadcastToMultipleSubscribers() {
        manager.registerBroadcast("user:created");
        manager.registerBroadcast("user:created");

        int sent = manager.broadcast("user:created", SsePayload.of("user:created", "hello"));

        assertThat(sent).isEqualTo(2);
    }

    @Test
    void shouldPushToTargetedReceiver() {
        manager.registerTargeted("user:123");
        manager.registerTargeted("user:123");

        int sent = manager.pushToReceiver("user:123", SsePayload.of("message", "hi", "user:123"));

        assertThat(sent).isEqualTo(2);
    }

    @Test
    void shouldReturnZeroWhenNoSubscribers() {
        int sent = manager.broadcast("nonexistent", SsePayload.of("x", "y"));
        assertThat(sent).isEqualTo(0);
    }

    @Test
    void shouldEnforceMaxEmitters() {
        SseProperties props = new SseProperties();
        props.setMaxEmitters(2);
        SseEmitterManager limited = new SseEmitterManager(props);

        limited.registerBroadcast("a");
        limited.registerBroadcast("b");

        assertThatThrownBy(() -> limited.registerBroadcast("c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("limit reached");
    }
}
