package com.frame.me.tester.async;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异步线程池自定义配置测试.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "me.async.thread-name-prefix=custom-async-")
class AsyncCustomPrefixTest {

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Test
    void customThreadNamePrefixShouldBeApplied() {
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("custom-async-");
    }
}
