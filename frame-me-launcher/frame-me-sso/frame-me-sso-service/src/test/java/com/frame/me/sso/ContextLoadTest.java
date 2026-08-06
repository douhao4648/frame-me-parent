package com.frame.me.sso;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * SSO 上下文加载测试.
 *
 * @author frame-me
 */
@ActiveProfiles("test")
@SpringBootTest
class ContextLoadTest {

    @Test
    void contextLoads() {
        // 验证 Spring 上下文能加载
    }
}
