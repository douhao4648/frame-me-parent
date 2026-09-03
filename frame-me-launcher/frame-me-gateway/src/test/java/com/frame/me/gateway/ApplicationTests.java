package com.frame.me.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 网关上下文加载测试.
 *
 * <p>关闭 Nacos 注册/配置外部依赖，仅验证 WebFlux 上下文与装配链可启动。</p>
 *
 * @author frame-me
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "me.gateway.auth.jwt.secret=0123456789abcdef0123456789abcdef"
})
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}
