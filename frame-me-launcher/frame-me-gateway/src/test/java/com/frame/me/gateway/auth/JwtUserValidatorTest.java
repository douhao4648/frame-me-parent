package com.frame.me.gateway.auth;

import com.frame.me.gateway.config.GatewayAuthProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JwtUserValidator} 测试.
 *
 * @author frame-me
 */
class JwtUserValidatorTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final GatewayAuthProperties props = new GatewayAuthProperties();

    {
        props.getJwt().setSecret(SECRET);
    }

    private final JwtUserValidator validator = new JwtUserValidator(props.getJwt());

    @Test
    void validAccessToken_returnsIdentity() {
        String token = sign("access", System.currentTimeMillis() + 60_000, "me");

        AuthIdentity identity = validator.validate(token).block();

        assertThat(identity).isNotNull();
        assertThat(identity.userId()).isEqualTo("1001");
        assertThat(identity.account()).isEqualTo("alice");
    }

    @Test
    void expiredToken_empty() {
        String token = sign("access", System.currentTimeMillis() - 60_000, "me");
        assertThat(validator.validate(token).blockOptional()).isEmpty();
    }

    @Test
    void tamperedToken_empty() {
        String token = sign("access", System.currentTimeMillis() + 60_000, "me");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThat(validator.validate(tampered).blockOptional()).isEmpty();
    }

    @Test
    void wrongIssuer_empty() {
        String token = sign("access", System.currentTimeMillis() + 60_000, "other");
        assertThat(validator.validate(token).blockOptional()).isEmpty();
    }

    @Test
    void refreshTokenRejected_empty() {
        String token = sign("refresh", System.currentTimeMillis() + 60_000, "me");
        assertThat(validator.validate(token).blockOptional()).isEmpty();
    }

    @Test
    void missingOrWeakSecret_failFast() {
        assertThatThrownBy(() -> new JwtUserValidator(new GatewayAuthProperties.Jwt()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");
        GatewayAuthProperties.Jwt weak = new GatewayAuthProperties.Jwt();
        weak.setSecret("short");
        assertThatThrownBy(() -> new JwtUserValidator(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("强度不足");
    }

    private String sign(String type, long expirationMillis, String issuer) {
        return Jwts.builder()
                .subject("1001")
                .claim("userId", 1001)
                .claim("account", "alice")
                .claim("type", type)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(expirationMillis))
                .signWith(KEY)
                .compact();
    }
}
