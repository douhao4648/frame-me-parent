package com.frame.me.sse.mvc.core;

import com.frame.me.sse.mvc.config.SseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SseEmitterManager} 单元测试.
 *
 * @author frame-me
 */
class SseEmitterManagerTest {

    private SseEmitterManager manager;
    private SseProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SseProperties();
        properties.setTimeout(60000L);
        manager = new SseEmitterManager(properties,
                java.util.Optional.of(com.frame.me.base.event.IReceiverIdAuthorizer.permitAll()));
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

    /**
     * 反向索引清理：移除某通道的广播 emitter 只清理其所属通道，
     * 其他通道的 emitter 与通道 key 不受影响（不遍历全部 eventType）.
     */
    @Test
    void shouldOnlyCleanOwnChannelOnBroadcastRemoval() {
        SseEmitter dead = manager.registerBroadcast("user:created");
        manager.registerBroadcast("order:paid");
        dead.complete();

        manager.broadcast("user:created", SsePayload.of("user:created", "x"));

        assertThat(manager.broadcastChannelCount()).isEqualTo(1);
        assertThat(manager.activeEmitterCount()).isEqualTo(1);
        assertThat(manager.broadcast("order:paid", SsePayload.of("order:paid", "y"))).isEqualTo(1);
    }

    @Test
    void shouldBroadcastToMultipleSubscribers() {        manager.registerBroadcast("user:created");
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
        SseEmitterManager limited = new SseEmitterManager(props,
                java.util.Optional.of(com.frame.me.base.event.IReceiverIdAuthorizer.permitAll()));

        limited.registerBroadcast("a");
        limited.registerBroadcast("b");

        // 超限返回 429 业务码（Too Many Requests，HTTP 200 + body），而非 500
        assertThatThrownBy(() -> limited.registerBroadcast("c"))
                .isInstanceOf(com.frame.me.base.exception.BusinessException.class)
                .satisfies(ex -> {
                    int code = ((com.frame.me.base.exception.BusinessException) ex).getCode();
                    assertThat(code).isEqualTo(429);
                });
    }

    @Test
    void shouldRejectBlankEventType() {
        assertThatThrownBy(() -> manager.registerBroadcast(""))
                .isInstanceOf(com.frame.me.base.exception.BusinessException.class)
                .satisfies(ex -> assertThat(((com.frame.me.base.exception.BusinessException) ex)
                        .getCode()).isEqualTo(400));
    }

    @Test
    void shouldRejectIllegalEventType() {
        // 含空格 / 斜杠等非法字符
        assertThatThrownBy(() -> manager.registerBroadcast("user created"))
                .isInstanceOf(com.frame.me.base.exception.BusinessException.class);
        assertThatThrownBy(() -> manager.registerBroadcast("user/created"))
                .isInstanceOf(com.frame.me.base.exception.BusinessException.class);
    }

    @Test
    void shouldRejectOverlongId() {
        String tooLong = "a".repeat(129);
        assertThatThrownBy(() -> manager.registerTargeted(tooLong))
                .isInstanceOf(com.frame.me.base.exception.BusinessException.class)
                .satisfies(ex -> assertThat(((com.frame.me.base.exception.BusinessException) ex)
                        .getCode()).isEqualTo(400));
    }

    @Test
    void shouldRejectBlankReceiverId() {
        assertThatThrownBy(() -> manager.registerTargeted(null))
                .isInstanceOf(com.frame.me.base.exception.BusinessException.class);
    }

    /**
     * 业务方注册了 IReceiverIdAuthorizer 且返回 false：拒绝订阅，403（fail-closed）.
     */
    @Test
    void shouldRejectTargetedWhenAuthorizerDenies() {
        com.frame.me.base.event.IReceiverIdAuthorizer denyAll = (String rid) -> false;
        SseEmitterManager guarded = new SseEmitterManager(properties, java.util.Optional.of(denyAll));

        assertThatThrownBy(() -> guarded.registerTargeted("user:123"))
                .isInstanceOf(com.frame.me.base.exception.BusinessException.class)
                .satisfies(ex -> assertThat(((com.frame.me.base.exception.BusinessException) ex)
                        .getCode()).isEqualTo(403));
        assertThat(guarded.activeEmitterCount()).isEqualTo(0);
    }

    /**
     * 业务方注册了 IReceiverIdAuthorizer 且返回 true：放行订阅.
     */
    @Test
    void shouldAllowTargetedWhenAuthorizerApproves() {
        com.frame.me.base.event.IReceiverIdAuthorizer allowAll = (String rid) -> true;
        SseEmitterManager guarded = new SseEmitterManager(properties, java.util.Optional.of(allowAll));

        SseEmitter emitter = guarded.registerTargeted("user:123");
        assertThat(emitter).isNotNull();
        assertThat(guarded.activeEmitterCount()).isEqualTo(1);
    }

    /**
     * 未注册 authorizer（Optional.empty）：fail-closed 拒绝——对象级越权防护不能默认放行.
     */
    @Test
    void shouldRejectTargetedWhenNoAuthorizer() {
        SseEmitterManager unguarded = new SseEmitterManager(properties, java.util.Optional.empty());

        assertThatThrownBy(() -> unguarded.registerTargeted("user:456"))
                .isInstanceOf(com.frame.me.base.exception.BusinessException.class)
                .satisfies(ex -> assertThat(((com.frame.me.base.exception.BusinessException) ex)
                        .getCode()).isEqualTo(403));
        assertThat(unguarded.activeEmitterCount()).isEqualTo(0);
    }

    /**
     * 并发 complete（触发 remove）与注册新 emitter：compute 原子清理保证不崩、结构自洽.
     *
     * <p>旧实现遍历中 remove key 触发 ConcurrentModificationException 或清空空集合期间新连接复用丢失；
     * compute 段内原子完成"移除元素 + 判空移除 key".</p>
     */
    @Test
    void shouldSurviveConcurrentRemoveAndRegister() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        java.util.List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < 50; j++) {
                    SseEmitter e = manager.registerBroadcast("user:created");
                    if ((idx + j) % 2 == 0) {
                        e.complete();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // 不抛 ConcurrentModificationException、结构自洽：广播清理残留 complete emitter 后，
        // 再广播命中数与存活数一致（幂等，无残留失败项）.
        manager.broadcast("user:created", SsePayload.of("user:created", "cleanup"));
        int sent = manager.broadcast("user:created", SsePayload.of("user:created", "hi"));
        assertThat(sent).isEqualTo(manager.activeEmitterCount());
    }

    /**
     * 最后一个旧连接清理与新连接注册并发：新连接不得加入已脱离 map 的旧集合.
     *
     * <p>旧实现路由注册在锁外 computeIfAbsent(...).add()：清理的 compute 可先判空移除 key，
     * 新连接的 add 随后落到已脱离 map 的旧集合——连接仍活跃并占用上限，却收不到该通道推送
     * （复现特征：active=1, channels=0）。修复后注册与清理共用 registerLock 互斥.</p>
     */
    @Test
    void shouldNotLoseRouteWhenLastEmitterRemovedDuringRegister() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int round = 0; round < 100; round++) {
            SseEmitterManager fresh = new SseEmitterManager(properties,
                    java.util.Optional.of(com.frame.me.base.event.IReceiverIdAuthorizer.permitAll()));
            SseEmitter old = fresh.registerBroadcast("user:created");
            CountDownLatch start = new CountDownLatch(1);
            // complete 的 emitter 经广播发送失败触发 removeEmitter，清空通道并移除 key
            // （单测环境无 MVC 基础设施，complete() 本身不触发 onCompletion 回调）
            Future<?> removeFuture = pool.submit(() -> {
                start.await();
                old.complete();
                fresh.broadcast("user:created", SsePayload.of("user:created", "cleanup"));
                return null;
            });
            Future<?> registerFuture = pool.submit(() -> {
                start.await();
                fresh.registerBroadcast("user:created");
                return null;
            });
            start.countDown();
            removeFuture.get();
            registerFuture.get();

            // 新连接必须仍挂在通道上：广播恰好命中 1 个存活 emitter；
            // 若竞态致其加入脱离 map 的旧集合，则 channels=0、sent=0
            assertThat(fresh.activeEmitterCount()).isEqualTo(1);
            int sent = fresh.broadcast("user:created", SsePayload.of("user:created", "hi"));
            assertThat(sent).as("round %d: 新注册 emitter 丢失路由", round).isEqualTo(1);
        }
        pool.shutdown();
    }

    /**
     * 心跳发送 SSE comment 保活：已 complete 的 emitter 发送失败被清理，存活 emitter 计入成功数.
     */
    @Test
    void heartbeatCleansDeadEmittersAndKeepsAlive() {
        SseEmitter alive = manager.registerBroadcast("user:created");
        SseEmitter dead = manager.registerBroadcast("user:created");
        dead.complete();

        int success = manager.heartbeat();

        // complete 的 emitter 发送 comment 失败被清理；存活 emitter 计入成功
        assertThat(manager.activeEmitterCount()).isEqualTo(1);
        assertThat(success).isEqualTo(1);
    }
}
