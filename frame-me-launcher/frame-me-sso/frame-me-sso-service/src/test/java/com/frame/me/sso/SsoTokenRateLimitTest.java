package com.frame.me.sso;

import com.frame.me.sso.api.enums.AccessType;
import com.frame.me.sso.entity.AppEntity;
import com.frame.me.sso.service.IAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * client_credentials 限流语义端到端：只限失败、不限成功.
 *
 * <p>独立测试类：主测试 profile 关闭限流（{@code me.auth.login-rate-limit.enabled=false}），
 * 本类用属性覆盖开启并收紧阈值（max-attempts=3，即第 4 次失败应 429）验证桶语义.
 * 两个用例各自注册独立应用，限流桶按 appId 隔离互不干扰.</p>
 *
 * @author frame-me
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "me.auth.login-rate-limit.enabled=true",
                "me.auth.login-rate-limit.max-attempts=3"})
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class SsoTokenRateLimitTest {

    private static final String CALLBACK = "http://client.local/cb";

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IAppService appService;

    /**
     * 合法 M2M 突发不限流：超过 max-attempts 次数的正确兑换全部成功（200），
     * 与文档"只限失败不限成功"一致.
     */
    @Test
    void successfulExchangesNotRateLimited() {
        AppEntity app = appService.register("合法突发应用", AccessType.EXTERNAL,
                List.of(CALLBACK), "openid");
        for (int i = 0; i < 6; i++) {
            Map<String, Object> res = token(app.getAppId(), app.getAppSecretPlain());
            assertThat(res.get("code")).as("第 %d 次合法兑换应成功", i + 1).isEqualTo(200);
        }
    }

    /**
     * 密钥爆破被限：前 3 次错误密钥 4001，第 4 次触发限流 429.
     */
    @Test
    void failedExchangesRateLimited() {
        AppEntity app = appService.register("被爆破应用", AccessType.EXTERNAL,
                List.of(CALLBACK), "openid");
        for (int i = 0; i < 3; i++) {
            assertThat(token(app.getAppId(), "wrong-secret").get("code"))
                    .as("第 %d 次错误密钥应为凭证错误", i + 1).isEqualTo(4001);
        }
        assertThat(token(app.getAppId(), "wrong-secret").get("code"))
                .as("第 4 次错误密钥应触发限流").isEqualTo(429);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> token(String appId, String secret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> res = rest.postForEntity("/api/auth/token",
                new HttpEntity<>(Map.of("grantType", "client_credentials",
                        "appId", appId, "appSecret", secret), headers), Map.class);
        return res.getBody();
    }
}
