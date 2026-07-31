package com.frame.me.tester.auth;

import com.frame.me.base.result.Result;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 认证端到端测试.
 *
 * @author frame-me
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JwtAuthEndToEndTest {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        // 设超时防 CI 卡死（连接 5s、读取 10s）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @SuppressWarnings("unchecked")
    @Test
    void testJwtAuthFlow() {
        // 1. 未登录访问 /base/auth/user 返回 401
        ResponseEntity<Result> meNoAuth = restTemplate.getForEntity(baseUrl() + "/base/auth/user", Result.class);
        assertEquals(HttpStatus.OK, meNoAuth.getStatusCode());
        assertEquals(401, meNoAuth.getBody().getCode());

        // 2. 登录获取 Token
        Map<String, String> loginBody = Map.of("account", "admin", "password", "123456");
        ResponseEntity<Result> loginResp = restTemplate.postForEntity(baseUrl() + "/base/auth/login", loginBody, Result.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        Result loginResult = loginResp.getBody();
        assertEquals(200, loginResult.getCode());
        assertNotNull(loginResult.getData());

        Map<String, String> tokenMap = (Map<String, String>) loginResult.getData();
        String accessToken = tokenMap.get("accessToken");
        String refreshToken = tokenMap.get("refreshToken");
        assertNotNull(accessToken);
        assertNotNull(refreshToken);

        // 3. 携带 Access Token 访问 /base/auth/user
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Result> meAuth = restTemplate.exchange(baseUrl() + "/base/auth/user", HttpMethod.GET, entity, Result.class);
        assertEquals(HttpStatus.OK, meAuth.getStatusCode());
        Result meResult = meAuth.getBody();
        assertEquals(200, meResult.getCode());
        Map<String, Object> userMap = (Map<String, Object>) meResult.getData();
        assertEquals("admin", userMap.get("account"));
        assertEquals(1L, ((Number) userMap.get("id")).longValue());

        // 4. 刷新 Token
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.setBearerAuth(refreshToken);
        HttpEntity<Void> refreshEntity = new HttpEntity<>(refreshHeaders);
        ResponseEntity<Result> refreshResp = restTemplate.exchange(baseUrl() + "/base/auth/refresh", HttpMethod.POST, refreshEntity, Result.class);
        assertEquals(HttpStatus.OK, refreshResp.getStatusCode());
        Result refreshResult = refreshResp.getBody();
        assertEquals(200, refreshResult.getCode());
        Map<String, String> newTokenMap = (Map<String, String>) refreshResult.getData();
        assertNotNull(newTokenMap.get("accessToken"));
        assertNotNull(newTokenMap.get("refreshToken"));

        // 5. 登出
        ResponseEntity<Result> logoutResp = restTemplate.exchange(baseUrl() + "/base/auth/logout", HttpMethod.POST, entity, Result.class);
        assertEquals(HttpStatus.OK, logoutResp.getStatusCode());
        assertEquals(200, logoutResp.getBody().getCode());

        // 6. 旧 Refresh Token 已失效
        ResponseEntity<Result> refreshAfterLogout = restTemplate.exchange(baseUrl() + "/base/auth/refresh", HttpMethod.POST, refreshEntity, Result.class);
        assertEquals(HttpStatus.OK, refreshAfterLogout.getStatusCode());
        assertEquals(401, refreshAfterLogout.getBody().getCode());
    }
}
