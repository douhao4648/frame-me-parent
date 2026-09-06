package com.frame.me.sso;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.frame.me.sso.infrastructure.satoken.SsoStpUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.GenericContainer;

/**
 * 验证 sa-token createLoginSession 对 Account-Session TTL 的影响.
 *
 * <p>用 sa-token 自己的 SaSession.timeout() 查 TTL（走 DAO 层，不经 StringRedisTemplate），
 * 避免 Redis 客户端路由差异。验证 updateMinTimeout 是否真的取较小值。</p>
 *
 * @author frame-me
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class SsoSessionTtlTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final long USER_ID = 888888L;

    @Test
    void createLoginSession_ttlBehavior() {
        // 1. 传 timeout=1天 建会话（模拟 app token）
        SsoStpUtil.STP_LOGIC.createLoginSession(USER_ID,
                new SaLoginParameter().setDeviceType("DEF").setTimeout(86400L));
        long ttl1 = printSessionTtl("新建(传1天)");

        // 2. 再传 timeout=7天（session 已存在，模拟浏览器登录后 app 授权）
        SsoStpUtil.STP_LOGIC.createLoginSession(USER_ID,
                new SaLoginParameter().setDeviceType("app-x").setTimeout(604800L));
        long ttl2 = printSessionTtl("再建(传7天)");

        // 3. 再传 timeout=1天（session 当前应=7天，看是否被缩短）
        SsoStpUtil.STP_LOGIC.createLoginSession(USER_ID,
                new SaLoginParameter().setDeviceType("app-y").setTimeout(86400L));
        long ttl3 = printSessionTtl("再建(传1天)");

        SsoStpUtil.STP_LOGIC.logout(USER_ID);

        System.out.println("=== 结论 ===");
        System.out.println("新建(传1天)  → session TTL = " + ttl1 + "秒 (" + (ttl1 / 3600) + "h)");
        System.out.println("再建(传7天)  → session TTL = " + ttl2 + "秒 (" + (ttl2 / 3600) + "h)");
        System.out.println("再建(传1天)  → session TTL = " + ttl3 + "秒 (" + (ttl3 / 3600) + "h)");
    }

    private long printSessionTtl(String label) {
        SaSession session = SsoStpUtil.STP_LOGIC.getSessionByLoginId(USER_ID, false);
        if (session == null) {
            System.out.println(label + " → session 不存在");
            return -1;
        }
        long ttl = session.timeout();
        System.out.println(label + " → session TTL = " + ttl + "秒 (" + (ttl / 3600) + "h)");
        return ttl;
    }
}
