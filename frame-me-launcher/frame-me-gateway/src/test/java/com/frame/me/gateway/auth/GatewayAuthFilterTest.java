package com.frame.me.gateway.auth;

import com.frame.me.gateway.config.GatewayAuthProperties;
import com.frame.me.gateway.config.GatewayConstant;
import com.frame.me.gateway.filter.GatewayAuthFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link GatewayAuthFilter} 测试：凭证驱动分发 + 实例级开关.
 *
 * @author frame-me
 */
class GatewayAuthFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private GatewayAuthProperties defaultProps() {
        GatewayAuthProperties props = new GatewayAuthProperties();
        props.getJwt().setSecret(SECRET);
        GatewayAuthProperties.AppCredential cred = new GatewayAuthProperties.AppCredential();
        cred.setAppKey("app-1");
        cred.setSecret("topsecret");
        props.getApps().add(cred);
        return props;
    }

    private GatewayAuthFilter filter(GatewayAuthProperties props,
                                     IUserValidator userValidator,
                                     IAppAuthenticator appAuthenticator) {
        return new GatewayAuthFilter(props, providerOf(userValidator), providerOf(appAuthenticator),
                new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }

    private static String validJwt() {
        return Jwts.builder()
                .subject("1001")
                .claim("userId", 1001)
                .claim("account", "alice")
                .claim("type", "access")
                .issuer("me")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void whitelistPath_passesWithoutCredentialButStripsHeaders() {
        GatewayAuthProperties props = defaultProps();
        props.getWhitelist().add("/api/auth/sso-login");
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/auth/sso-login")
                .header(GatewayConstant.HEADER_USER_ID, "999"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter(props, new JwtUserValidator(props.getJwt()), new ConfigAppAuthenticator(props.getApps()))
                .filter(exchange, recordingChain(captured)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst(GatewayConstant.HEADER_USER_ID)).isNull();
    }

    @Test
    void validJwt_injectsIdentityHeaders() {
        GatewayAuthProperties props = defaultProps();
        // 同时携带伪造身份头：应先剥离再由网关注入正确值
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/x")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt())
                .header(GatewayConstant.HEADER_USER_ID, "999"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter(props, new JwtUserValidator(props.getJwt()), new ConfigAppAuthenticator(props.getApps()))
                .filter(exchange, recordingChain(captured)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst(GatewayConstant.HEADER_USER_ID)).isEqualTo("1001");
        assertThat(captured.get().getRequest().getHeaders().getFirst(GatewayConstant.HEADER_USER_ACCOUNT)).isEqualTo("alice");
    }

    @Test
    void tamperedJwt_401() {
        GatewayAuthProperties props = defaultProps();
        String tampered = validJwt().replaceAll("..$", "xx");
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/x")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered));

        filter(props, new JwtUserValidator(props.getJwt()), new ConfigAppAuthenticator(props.getApps()))
                .filter(exchange, noopChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validSignature_passesAndInjectsAppKey() {
        GatewayAuthProperties props = defaultProps();
        String date = ConfigAppAuthenticator.httpDate();
        String sign = ConfigAppAuthenticator.sign("topsecret", "app-1", "GET", "/api/data", date);
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, ConfigAppAuthenticator.authorizationHeader("app-1", sign))
                .header(HttpHeaders.DATE, date)
                .header(GatewayConstant.HEADER_USER_ID, "999"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter(props, new JwtUserValidator(props.getJwt()), new ConfigAppAuthenticator(props.getApps()))
                .filter(exchange, recordingChain(captured)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst(GatewayConstant.HEADER_APP_KEY)).isEqualTo("app-1");
        assertThat(captured.get().getRequest().getHeaders().getFirst(GatewayConstant.HEADER_USER_ID)).isNull();
    }

    @Test
    void badSignature_401() {
        GatewayAuthProperties props = defaultProps();
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION,
                        ConfigAppAuthenticator.authorizationHeader("app-1", "aGVsbG8="))
                .header(HttpHeaders.DATE, ConfigAppAuthenticator.httpDate()));

        filter(props, new JwtUserValidator(props.getJwt()), new ConfigAppAuthenticator(props.getApps()))
                .filter(exchange, noopChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void noCredential_allowAnonymousFalse_401() {
        GatewayAuthProperties props = defaultProps();
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/x"));

        filter(props, new JwtUserValidator(props.getJwt()), new ConfigAppAuthenticator(props.getApps()))
                .filter(exchange, noopChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void noCredential_allowAnonymousTrue_stripsHeadersAndPasses() {
        GatewayAuthProperties props = defaultProps();
        props.setAllowAnonymous(true);
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/x")
                .header(GatewayConstant.HEADER_USER_ID, "999")
                .header(GatewayConstant.HEADER_APP_KEY, "forged"));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter(props, new JwtUserValidator(props.getJwt()), new ConfigAppAuthenticator(props.getApps()))
                .filter(exchange, recordingChain(captured)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst(GatewayConstant.HEADER_USER_ID)).isNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst(GatewayConstant.HEADER_APP_KEY)).isNull();
    }

    @Test
    void signatureTakesPriority_overSaTokenHeader() {
        // sa-token 模式下 tokenName 头与 Signature 并存：Signature 是显式 app 意图，走 app 分支
        GatewayAuthProperties props = defaultProps();
        props.setUserValidator(GatewayAuthProperties.UserValidatorType.SA_TOKEN);
        IUserValidator userValidator = mock(IUserValidator.class);
        String date = ConfigAppAuthenticator.httpDate();
        String sign = ConfigAppAuthenticator.sign("topsecret", "app-1", "GET", "/api/data", date);
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/data")
                .header(props.getSaToken().getTokenName(), "some-satoken")
                .header(HttpHeaders.AUTHORIZATION, ConfigAppAuthenticator.authorizationHeader("app-1", sign))
                .header(HttpHeaders.DATE, date));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter(props, userValidator, new ConfigAppAuthenticator(props.getApps()))
                .filter(exchange, recordingChain(captured)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst(GatewayConstant.HEADER_APP_KEY)).isEqualTo("app-1");
        verifyNoInteractions(userValidator);
    }

    @Test
    void userAuthDisabled_validJwt_401() {
        GatewayAuthProperties props = defaultProps();
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/x")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt()));

        filter(props, null, new ConfigAppAuthenticator(props.getApps()))
                .filter(exchange, noopChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void appAuthDisabled_validSignature_401() {
        GatewayAuthProperties props = defaultProps();
        String date = ConfigAppAuthenticator.httpDate();
        String sign = ConfigAppAuthenticator.sign("topsecret", "app-1", "GET", "/api/data", date);
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/data")
                .header(HttpHeaders.AUTHORIZATION, ConfigAppAuthenticator.authorizationHeader("app-1", sign))
                .header(HttpHeaders.DATE, date));

        filter(props, new JwtUserValidator(props.getJwt()), null)
                .filter(exchange, noopChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * digest 声明且 body 匹配：验签+摘要通过后，下游读到的 body 必须是认证阶段校验过的同一份字节.
     */
    @Test
    void digestSigned_bodyReplayedDownstream() {
        assertDigestBodyReplayed("{\"amount\":1}");
    }

    @Test
    void digestSigned_largeBodyReplayedDownstream() {
        assertDigestBodyReplayed("a".repeat(2 * 1024 * 1024 + 17));
    }

    private void assertDigestBodyReplayed(String content) {
        GatewayAuthProperties props = defaultProps();
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        String date = ConfigAppAuthenticator.httpDate();
        String digest = "SHA-256=" + java.util.Base64.getEncoder().encodeToString(sha256(body));
        String signingString = "app-1\n" + "date: " + date + "\n" + "POST /api/pay\n"
                + "digest: " + digest + "\n";
        String signature = java.util.Base64.getEncoder().encodeToString(hmacSha256("topsecret", signingString));
        String authz = "Signature keyId=\"app-1\",algorithm=\"hmac-sha256\","
                + "headers=\"date @request-target digest\",signature=\"" + signature + "\"";
        // body() 直接返回 request（非 builder），from 有 MockServerHttpRequest 重载
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/pay")
                .header(HttpHeaders.AUTHORIZATION, authz)
                .header(HttpHeaders.DATE, date)
                .header("Digest", digest)
                .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(body.length))
                .body(new String(body, StandardCharsets.UTF_8)));
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        AtomicReference<byte[]> downstream = new AtomicReference<>();

        filter(props, new JwtUserValidator(props.getJwt()), new ConfigAppAuthenticator(props.getApps()))
                .filter(exchange, ex -> {
                    captured.set(ex);
                    ex.getResponse().setStatusCode(HttpStatus.OK);
                    return DataBufferUtils.join(ex.getRequest().getBody())
                            .doOnNext(buffer -> {
                                byte[] bytes = new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                DataBufferUtils.release(buffer);
                                downstream.set(bytes);
                            }).then(ex.getResponse().setComplete());
                }).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst(GatewayConstant.HEADER_APP_KEY))
                .isEqualTo("app-1");
        assertThat(downstream.get()).isEqualTo(body);
        assertThat(captured.get().getRequest().getHeaders().getContentLength()).isEqualTo(body.length);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static byte[] sha256(byte[] body) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmacSha256(String secret, String signingString) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }

    private static GatewayFilterChain recordingChain(AtomicReference<ServerWebExchange> captured) {
        return ex -> {
            captured.set(ex);
            return Mono.empty();
        };
    }

    private static GatewayFilterChain noopChain() {
        return ex -> Mono.empty();
    }
}
