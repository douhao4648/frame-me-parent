package com.frame.me.tester;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot 上下文加载测试.
 *
 * <p>使用 H2 内存数据库，不依赖 Docker，仅验证 Spring Boot 上下文能正常启动。
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationTests {

    @Test
    void contextLoads() {
    }

}
