package com.frame.me.audit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文加载测试.
 *
 * <p>验证 Bean 装配无环、无缺失依赖。test profile 用 H2 + 内嵌 sa-token 内存 DAO，
 * 避免强依赖外部 MySQL/Redis 即可跑通。</p>
 *
 * @author frame-me
 */
@SpringBootTest
@ActiveProfiles("test")
class ContextLoadTest {

    @Test
    void contextLoads() {
        // 仅验证上下文能启动
    }
}
