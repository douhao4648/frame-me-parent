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
}
