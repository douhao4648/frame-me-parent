package com.frame.me.gateway.auth;

import com.frame.me.gateway.config.GatewayAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConfigAppAuthenticator} 测试（APISIX hmac-auth 契约）.
 *
 * @author frame-me
 */
class ConfigAppAuthenticatorTest {

    private final ConfigAppAuthenticator authenticator = new ConfigAppAuthenticator(List.of(credential()));

    @Test
    void validSignature_returnsAppKey() {
        assertThat(authenticator.authenticate(signedGet("/api/data", "app-1", "topsecret")))
                .isEqualTo("app-1");
    }

    @Test
    void missingAuthorizationHeader_null() {
        assertThat(authenticator.authenticate(MockServerHttpRequest.get("/api/data").build())).isNull();
    }

    @Test
    void unknownKeyId_null() {
        assertThat(authenticator.authenticate(signedGet("/api/data", "ghost", "topsecret"))).isNull();
    }

    @Test
    void staleDate_null() {
        String staleDate = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        String sign = ConfigAppAuthenticator.sign("topsecret", "app-1", "GET", "/api/data", staleDate);
        assertThat(authenticator.authenticate(MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, ConfigAppAuthenticator.authorizationHeader("app-1", sign))
                .header(HttpHeaders.DATE, staleDate)
                .build())).isNull();
    }

    @Test
    void signatureForOtherPath_null() {
        String date = ConfigAppAuthenticator.httpDate();
        String sign = ConfigAppAuthenticator.sign("topsecret", "app-1", "GET", "/api/other", date);
        assertThat(authenticator.authenticate(MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, ConfigAppAuthenticator.authorizationHeader("app-1", sign))
                .header(HttpHeaders.DATE, date)
                .build())).isNull();
    }

    @Test
    void malformedBase64Signature_null() {
        assertThat(authenticator.authenticate(MockServerHttpRequest.get("/api/data")
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
        assertThat(authenticator.authenticate(MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, authz)
                .header(HttpHeaders.DATE, date)
                .build())).isNull();
    }

    /**
     * 对齐 APISIX request_uri：query string 参与签名，带 query 的请求验签通过.
     */
    @Test
    void queryString_signedAndVerified() {
        assertThat(authenticator.authenticate(signedGet("/api/data?x=1&y=2", "app-1", "topsecret")))
                .isEqualTo("app-1");
    }

    /**
     * query 篡改检出：按 ?x=1 签名、请求 ?x=2 → 拒绝.
     */
    @Test
    void queryTampered_null() {
        String date = ConfigAppAuthenticator.httpDate();
        String sign = ConfigAppAuthenticator.sign("topsecret", "app-1", "GET", "/api/data?x=1", date);
        assertThat(authenticator.authenticate(MockServerHttpRequest.get("/api/data?x=2")
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
        assertThat(authenticator.authenticate(MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, authz)
                .header(HttpHeaders.DATE, date)
                .build())).isEqualTo("app-1");
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
