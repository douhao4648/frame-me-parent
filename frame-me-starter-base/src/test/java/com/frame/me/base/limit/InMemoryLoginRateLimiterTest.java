package com.frame.me.base.limit;

import com.frame.me.base.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link InMemoryLoginRateLimiter} 测试.
 *
 * @author frame-me
 */
class InMemoryLoginRateLimiterTest {

    @Test
    void acquire_throwsWhenOverMaxAttempts() {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(2, Duration.ofMinutes(1));
        limiter.acquire("a");
        limiter.acquire("a");

        assertThatThrownBy(() -> limiter.acquire("a")).isInstanceOf(BusinessException.class);
    }

    /**
     * 淘汰：容量超阈值时清掉窗口已过期条目（攻击者轮换伪造 IP 头刷 key 不致 map 无限膨胀）.
     */
    @Test
    @SuppressWarnings("unchecked")
    void acquire_evictsExpiredEntriesWhenOverThreshold() throws Exception {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(5, Duration.ofMillis(50));
        for (int i = 0; i <= 10_000; i++) {
            limiter.acquire("ip-" + i);
        }
        Thread.sleep(60);

        limiter.acquire("trigger"); // size 超阈值，触发过期窗口清理

        Field field = InMemoryLoginRateLimiter.class.getDeclaredField("store");
        field.setAccessible(true);
        Map<String, long[]> store = (Map<String, long[]>) field.get(limiter);
        assertThat(store.size()).isLessThan(100);
    }

    /**
     * 硬上限：桶满后拒绝为新 key 建桶（不淘汰活跃桶——淘汰等于重置攻击者额度），
     * 已有桶的 key 不受影响.
     */
    @Test
    @SuppressWarnings("unchecked")
    void acquire_rejectsNewKeyWhenAtHardCapButAllowsExisting() throws Exception {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(5, Duration.ofMinutes(1));
        for (int i = 0; i < 20_000; i++) {
            limiter.acquire("ip-" + i);
        }

        assertThatThrownBy(() -> limiter.acquire("new-ip"))
                .isInstanceOf(BusinessException.class);

        limiter.acquire("ip-0"); // 已有桶不受硬上限影响

        Field field = InMemoryLoginRateLimiter.class.getDeclaredField("store");
        field.setAccessible(true);
        Map<String, long[]> store = (Map<String, long[]>) field.get(limiter);
        assertThat(store).hasSize(20_000);
        assertThat(store.get("ip-0")[1]).isEqualTo(2);
    }

    @Test
    void concurrentAdmissions_neverExceedCapacity() throws Exception {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(5, Duration.ofHours(1));
        for (int i = 0; i < 19_999; i++) {
            limiter.acquire("ip-" + i);
        }
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(32);
        var admitted = new java.util.concurrent.atomic.AtomicInteger();
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 128; i++) {
                String key = "new-" + i;
                futures.add(executor.submit(() -> {
                    try {
                        start.await();
                        limiter.acquire(key);
                        admitted.incrementAndGet();
                    } catch (BusinessException expected) {
                        assertThat(expected.getCode()).isEqualTo(429);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertThat(admitted.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
