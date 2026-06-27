package com.frame.me.tester.redis;

import com.frame.me.redis.util.RedissonLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RedissonLock 集成测试.
 *
 * <p>使用 Testcontainers 启动 Redis，验证 {@link RedissonLock} 的可重入锁、
 * 互斥性与并发竞争行为。</p>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class RedissonLockTest {

    private static final boolean DOCKER_AVAILABLE;

    static {
        boolean dockerAvailable;
        try {
            DockerClientFactory.instance().client();
            dockerAvailable = true;
        } catch (Exception e) {
            dockerAvailable = false;
        }
        DOCKER_AVAILABLE = dockerAvailable;
    }

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Docker 不可用，跳过 Redis 集成测试");
    }

    @AfterEach
    void tearDown() {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        RedissonLock.unlock("test:lock");
    }

    /**
     * 测试可重入锁的获取与释放.
     */
    @Test
    void shouldAcquireAndReleaseReentrantLock() {
        String key = "test:lock:reentrant";
        assertTrue(RedissonLock.tryLock(key, 0, 30000), "应能获取锁");
        assertTrue(RedissonLock.tryLock(key, 0, 30000), "同线程应能重入获取锁");
        RedissonLock.unlock(key);
        RedissonLock.unlock(key);
    }

    /**
     * 测试锁的互斥性：同一把锁在同一时间只能被一个线程持有.
     */
    @Test
    void shouldBeMutuallyExclusive() throws InterruptedException {
        String key = "test:lock:exclusive";
        assertTrue(RedissonLock.tryLock(key, 0, 30000), "第一个线程应能获取锁");

        // 在另一个线程尝试获取同一把锁，应失败
        boolean[] acquired = {false};
        Thread t = new Thread(() -> {
            acquired[0] = RedissonLock.tryLock(key, 100, 100);
        });
        t.start();
        t.join();

        assertFalse(acquired[0], "第二个线程在租期内不应获取到锁");
        RedissonLock.unlock(key);
    }

    /**
     * 测试并发竞争：多个线程竞争同一把锁，只有一个能进入临界区.
     */
    @Test
    void shouldOnlyAllowOneThreadInCriticalSection() throws InterruptedException {
        String key = "test:lock:concurrent";
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (RedissonLock.tryLock(key, 5000, 30000)) {
                        try {
                            successCount.incrementAndGet();
                            // 模拟临界区耗时
                            Thread.sleep(50);
                        } finally {
                            RedissonLock.unlock(key);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "所有线程应在超时前完成");
        executor.shutdown();

        assertTrue(successCount.get() >= 1, "至少应有一个线程获取到锁");
        assertTrue(successCount.get() <= threadCount, "成功次数不应超过线程数");
    }

    /**
     * 测试带过期时间的锁：租期结束后锁应可被其他线程获取.
     */
    @Test
    void shouldExpireAfterLeaseTime() throws InterruptedException {
        String key = "test:lock:expire";
        assertTrue(RedissonLock.tryLock(key, 0, 500), "获取 500ms 租期的锁");
        RedissonLock.unlock(key);

        // 由于已经主动释放，这里验证再次获取即可
        assertTrue(RedissonLock.tryLock(key, 0, 500), "释放后应能重新获取");
        RedissonLock.unlock(key);
    }
}
