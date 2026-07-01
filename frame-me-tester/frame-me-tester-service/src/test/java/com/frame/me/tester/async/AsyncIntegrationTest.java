package com.frame.me.tester.async;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认异步线程池集成测试.
 */
@SpringBootTest
@ActiveProfiles("test")
class AsyncIntegrationTest {

    @Autowired
    private AsyncDemoService asyncDemoService;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Test
    void asyncMethodShouldRunInAsyncThread() throws Exception {
        String callerThreadName = Thread.currentThread().getName();
        CompletableFuture<String> future = asyncDemoService.asyncThreadName();
        String asyncThreadName = future.get(5, TimeUnit.SECONDS);

        assertThat(asyncThreadName)
                .isNotEqualTo(callerThreadName)
                .startsWith("frame-me-async-");
    }

    @Test
    void defaultThreadPoolShouldBeConfigured() {
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(16);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(256);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("frame-me-async-");
    }
}
