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
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 权限注解 + Filter 路径规则集成测试.
 *
 * @author frame-me
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PermissionIntegrationTest {

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

        // 未登录：401（由 AuthFilter 强制登录产生）
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(401, response.getBody().getCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testAuthorizedWithJwtToken() {
        HttpEntity<Void> entity = bearerEntity();

        ResponseEntity<Result> adminResponse = restTemplate.exchange(
                baseUrl() + "/api/perm-test/admin", HttpMethod.GET, entity, Result.class);
        assertEquals(HttpStatus.OK, adminResponse.getStatusCode());
        assertEquals(200, adminResponse.getBody().getCode());

        ResponseEntity<Result> orderResponse = restTemplate.exchange(
                baseUrl() + "/api/perm-test/order", HttpMethod.GET, entity, Result.class);
        assertEquals(HttpStatus.OK, orderResponse.getStatusCode());
        assertEquals(200, orderResponse.getBody().getCode());
    }

    @Test
    void testForbiddenWhenLoggedInButNoPermission() {
        // admin 已登录，但没有 superadmin 角色 → RBAC 拦截器返回 403
        ResponseEntity<Result> response = restTemplate.exchange(
                baseUrl() + "/api/perm-test/super", HttpMethod.GET, bearerEntity(), Result.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(403, response.getBody().getCode());
    }

    @Test
    void testFilterRulePass() {
        // 命中 Filter 规则 role('admin')，admin 满足 → 放行
        ResponseEntity<Result> response = restTemplate.exchange(
                baseUrl() + "/api/filter-test/data", HttpMethod.GET, bearerEntity(), Result.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getCode());
    }

    @Test
    void testFilterRuleForbidden() {
        // 命中 Filter 规则 role('superadmin')，admin 不满足 → Filter 返回 403
        ResponseEntity<Result> response = restTemplate.exchange(
                baseUrl() + "/api/filter-deny/data", HttpMethod.GET, bearerEntity(), Result.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(403, response.getBody().getCode());
    }

    private HttpEntity<Void> bearerEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAndGetAccessToken());
        return new HttpEntity<>(headers);
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

        @Bean
        FilterTestController filterTestController() {
            return new FilterTestController();
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

        @RequireAuth("role('superadmin')")
        @GetMapping("/super")
        public IResult<String> superEndpoint() {
            return Result.success("super");
        }
    }

    /**
     * 仅用于 Filter 层路径规则测试，方法本身不标 {@link RequireAuth}.
     */
    @RestController
    static class FilterTestController {

        @GetMapping("/api/filter-test/data")
        public IResult<String> data() {
            return Result.success("filter-ok");
        }

        @GetMapping("/api/filter-deny/data")
        public IResult<String> deny() {
            return Result.success("filter-deny");
        }
    }
}
