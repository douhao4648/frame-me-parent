package com.frame.me.tester.auth;

import com.frame.me.api.result.IResult;
import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.rbac.annotation.RequireAuth;
import com.frame.me.base.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 权限注解集成测试.
 *
 * @author frame-me
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PermissionIntegrationTest {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void testAnonymousAccessPublicEndpoint() {
        ResponseEntity<Result> response = restTemplate.getForEntity(
                baseUrl() + "/api/perm-test/public", Result.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getCode());
    }

    @Test
    void testUnauthorizedAccessProtectedEndpoint() {
        ResponseEntity<Result> response = restTemplate.getForEntity(
                baseUrl() + "/api/perm-test/admin", Result.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(401, response.getBody().getCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testAuthorizedWithJwtToken() {
        String accessToken = loginAndGetAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Result> adminResponse = restTemplate.exchange(
                baseUrl() + "/api/perm-test/admin", HttpMethod.GET, entity, Result.class);
        assertEquals(HttpStatus.OK, adminResponse.getStatusCode());
        assertEquals(200, adminResponse.getBody().getCode());

        ResponseEntity<Result> orderResponse = restTemplate.exchange(
                baseUrl() + "/api/perm-test/order", HttpMethod.GET, entity, Result.class);
        assertEquals(HttpStatus.OK, orderResponse.getStatusCode());
        assertEquals(200, orderResponse.getBody().getCode());
    }

    @SuppressWarnings("unchecked")
    private String loginAndGetAccessToken() {
        Map<String, String> loginBody = Map.of("account", "admin", "password", "123456");
        ResponseEntity<Result> loginResp = restTemplate.postForEntity(
                baseUrl() + "/base/auth/login", loginBody, Result.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        assertEquals(200, loginResp.getBody().getCode());

        Map<String, String> tokenMap = (Map<String, String>) loginResp.getBody().getData();
        String accessToken = tokenMap.get("accessToken");
        assertNotNull(accessToken);
        return accessToken;
    }

    @TestConfiguration
    static class Config {

        @Bean
        PermissionTestController permissionTestController() {
            return new PermissionTestController();
        }
    }

    @RestController
    @RequestMapping("/api/perm-test")
    static class PermissionTestController {

        @Anonymous
        @GetMapping("/public")
        public IResult<String> publicEndpoint() {
            return Result.success("public");
        }

        @RequireAuth("role('admin')")
        @GetMapping("/admin")
        public IResult<String> adminEndpoint() {
            return Result.success("admin");
        }

        @RequireAuth("perm('order', 'r')")
        @GetMapping("/order")
        public IResult<String> orderEndpoint() {
            return Result.success("order");
        }
    }
}
