package com.frame.me.gateway.auth;

import com.frame.me.gateway.config.GatewayAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ConfigAppAuthenticator} 测试（APISIX hmac-auth 契约）.
 *
 * @author frame-me
 */
class ConfigAppAuthenticatorTest {

    private final ConfigAppAuthenticator authenticator = new ConfigAppAuthenticator(List.of(credential()));

    @Test
    void validSignature_returnsAppKey() {
        assertThat(auth(authenticator, signedGet("/api/data", "app-1", "topsecret")))
                .isEqualTo("app-1");
    }

    @Test
    void missingAuthorizationHeader_null() {
        assertThat(auth(authenticator, MockServerHttpRequest.get("/api/data").build())).isNull();
    }

    @Test
    void unknownKeyId_null() {
        assertThat(auth(authenticator, signedGet("/api/data", "ghost", "topsecret"))).isNull();
    }

    @Test
    void staleDate_null() {
        String staleDate = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        String sign = ConfigAppAuthenticator.sign("topsecret", "app-1", "GET", "/api/data", staleDate);
        assertThat(auth(authenticator, MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, ConfigAppAuthenticator.authorizationHeader("app-1", sign))
                .header(HttpHeaders.DATE, staleDate)
                .build())).isNull();
    }

    @Test
    void signatureForOtherPath_null() {
        String date = ConfigAppAuthenticator.httpDate();
        String sign = ConfigAppAuthenticator.sign("topsecret", "app-1", "GET", "/api/other", date);
        assertThat(auth(authenticator, MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, ConfigAppAuthenticator.authorizationHeader("app-1", sign))
                .header(HttpHeaders.DATE, date)
                .build())).isNull();
    }

    @Test
    void malformedBase64Signature_null() {
        assertThat(auth(authenticator, MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, ConfigAppAuthenticator.authorizationHeader("app-1", "!!!not-base64!!!"))
                .header(HttpHeaders.DATE, ConfigAppAuthenticator.httpDate())
                .build())).isNull();
    }

    /**
     * 防降级：headers 参数不含 date 时拒绝（否则防重放失效）.
     */
    @Test
    void downgradeMissingDateInSignedHeaders_null() {
        String date = ConfigAppAuthenticator.httpDate();
        String authz = "Signature keyId=\"app-1\",algorithm=\"hmac-sha256\","
                + "headers=\"@request-target\",signature=\""
                + ConfigAppAuthenticator.sign("topsecret", "app-1", "GET", "/api/data", date) + "\"";
        assertThat(auth(authenticator, MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, authz)
                .header(HttpHeaders.DATE, date)
                .build())).isNull();
    }

    /**
     * 对齐 APISIX request_uri：query string 参与签名，带 query 的请求验签通过.
     */
    @Test
    void queryString_signedAndVerified() {
        assertThat(auth(authenticator, signedGet("/api/data?x=1&y=2", "app-1", "topsecret")))
                .isEqualTo("app-1");
    }

    /**
     * query 篡改检出：按 ?x=1 签名、请求 ?x=2 → 拒绝.
     */
    @Test
    void queryTampered_null() {
        String date = ConfigAppAuthenticator.httpDate();
        String sign = ConfigAppAuthenticator.sign("topsecret", "app-1", "GET", "/api/data?x=1", date);
        assertThat(auth(authenticator, MockServerHttpRequest.get("/api/data?x=2")
                .header(HttpHeaders.AUTHORIZATION, ConfigAppAuthenticator.authorizationHeader("app-1", sign))
                .header(HttpHeaders.DATE, date)
                .build())).isNull();
    }

    /**
     * 对齐 APISIX：签串头名保留 headers 参数声明的原样大小写（声明大写 Date 也能验过）.
     */
    @Test
    void declaredHeaderCasePreserved() {
        String date = ConfigAppAuthenticator.httpDate();
        String signingString = "app-1\n" + "Date: " + date + "\n" + "GET /api/data\n";
        String signature = java.util.Base64.getEncoder().encodeToString(hmacSha256("topsecret", signingString));
        String authz = "Signature keyId=\"app-1\",algorithm=\"hmac-sha256\","
                + "headers=\"Date @request-target\",signature=\"" + signature + "\"";
        assertThat(auth(authenticator, MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, authz)
                .header(HttpHeaders.DATE, date)
                .build())).isEqualTo("app-1");
    }

    /**
     * nonce 防重放：声明 x-nonce 且首次使用 → 通过.
     */
    @Test
    void nonceDeclared_freshNonce_passes() {
        ReactiveStringRedisTemplate redis = redisReturning(true);
        ConfigAppAuthenticator withRedis = new ConfigAppAuthenticator(List.of(credential()), redis);
        assertThat(auth(withRedis, signedGetWithNonce("/api/data", "nonce-1"))).isEqualTo("app-1");
    }

    /**
     * nonce 防重放：同一 nonce 第二次到达（SET NX 未抢到）→ 拒绝.
     */
    @Test
    void nonceDeclared_replayedNonce_null() {
        ReactiveStringRedisTemplate redis = redisReturning(false);
        ConfigAppAuthenticator withRedis = new ConfigAppAuthenticator(List.of(credential()), redis);
        assertThat(auth(withRedis, signedGetWithNonce("/api/data", "nonce-1"))).isNull();
    }

    /**
     * nonce 防重放：声明了 nonce 但网关未配 Redis → fail-closed 拒绝.
     */
    @Test
    void nonceDeclared_noRedis_null() {
        assertThat(auth(authenticator, signedGetWithNonce("/api/data", "nonce-1"))).isNull();
    }

    /**
     * nonce 防重放：Redis 故障 → fail-closed 拒绝（nonce 是显式 opt-in）.
     */
    @Test
    void nonceDeclared_redisDown_null() {
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        when(ops.setIfAbsent(any(), any(), any(Duration.class)))
                .thenReturn(Mono.error(new DataAccessResourceFailureException("connection refused")));
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(ops);
        ConfigAppAuthenticator withRedis = new ConfigAppAuthenticator(List.of(credential()), redis);
        assertThat(auth(withRedis, signedGetWithNonce("/api/data", "nonce-1"))).isNull();
    }

    /**
     * 不声明 nonce：维持 APISIX 基线行为（仅时钟窗），不触碰 Redis.
     */
    @Test
    void nonceNotDeclared_neverTouchesRedis() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        ConfigAppAuthenticator withRedis = new ConfigAppAuthenticator(List.of(credential()), redis);
        assertThat(auth(withRedis, signedGet("/api/data", "app-1", "topsecret"))).isEqualTo("app-1");
        verify(redis, never()).opsForValue();
    }

    /**
     * 声明 nonce 但签名非法：验签失败先行，不消费 nonce（防烧 nonce DoS）.
     */
    @Test
    void badSignature_nonceNotConsumed() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        ConfigAppAuthenticator withRedis = new ConfigAppAuthenticator(List.of(credential()), redis);
        String date = ConfigAppAuthenticator.httpDate();
        String authz = "Signature keyId=\"app-1\",algorithm=\"hmac-sha256\","
                + "headers=\"date @request-target x-nonce\",signature=\""
                + ConfigAppAuthenticator.sign("wrongsecret", "app-1", "GET", "/api/data", date) + "\"";
        assertThat(auth(withRedis, MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, authz)
                .header(HttpHeaders.DATE, date)
                .header("X-Nonce", "nonce-1")
                .build())).isNull();
        verify(redis, never()).opsForValue();
    }

    private static ReactiveStringRedisTemplate redisReturning(boolean setNxResult) {
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        when(ops.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(Mono.just(setNxResult));
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(ops);
        return redis;
    }

    private static ServerHttpRequest signedGetWithNonce(String path, String nonce) {
        String date = ConfigAppAuthenticator.httpDate();
        String signingString = "app-1\n" + "date: " + date + "\n" + "GET " + path + "\n"
                + "x-nonce: " + nonce + "\n";
        String signature = java.util.Base64.getEncoder().encodeToString(hmacSha256("topsecret", signingString));
        String authz = "Signature keyId=\"app-1\",algorithm=\"hmac-sha256\","
                + "headers=\"date @request-target x-nonce\",signature=\"" + signature + "\"";
        return MockServerHttpRequest.get(path)
                .header(HttpHeaders.AUTHORIZATION, authz)
                .header(HttpHeaders.DATE, date)
                .header("X-Nonce", nonce)
                .build();
    }

    /**
     * 同步取认证结果（测试便利；生产侧为 {@code Mono} 由 filter 订阅）.
     */
    private static String auth(ConfigAppAuthenticator authenticator, ServerHttpRequest request) {
        return authenticator.authenticate(request).block();
    }

    private static byte[] hmacSha256(String secret, String signingString) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ServerHttpRequest signedGet(String path, String keyId, String secret) {
        String date = ConfigAppAuthenticator.httpDate();
        String sign = ConfigAppAuthenticator.sign(secret, keyId, "GET", path, date);
        return MockServerHttpRequest.get(path)
                .header(HttpHeaders.AUTHORIZATION, ConfigAppAuthenticator.authorizationHeader(keyId, sign))
                .header(HttpHeaders.DATE, date)
                .build();
    }

    private static GatewayAuthProperties.AppCredential credential() {
        GatewayAuthProperties.AppCredential c = new GatewayAuthProperties.AppCredential();
        c.setAppKey("app-1");
        c.setSecret("topsecret");
        return c;
    }
}
