package com.frame.me.gateway.auth;

import com.frame.me.gateway.config.GatewayAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 用户 token 验证器：本地验签下游 auth-jwt 签发的 access token（无状态，不查 Redis）.
 *
 * <p>约定与 {@code frame-me-starter-auth-jwt} 的 JwtTokenService 对齐（docs/guides/gateway.md
 * 维护漂移防线）：HS256 共享密钥（≥256 位）、{@code iss} 必须匹配、
 * {@code type=access}、{@code userId}/{@code account} claims。</p>
 *
 * <p>吊销语义：踢人后 refresh token 即时失效，已签发 access token 在自然过期前仍可通过
 * （窗口 = 下游 {@code me.auth.jwt.access-token-expires}，默认 2h），网关层不做即时踢人。</p>
 *
 * @author frame-me
 */
@Slf4j
public class JwtUserValidator implements IUserValidator {

    /**
     * HMAC 密钥最低字节数（256 位），与 JwtTokenService 一致.
     */
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey secretKey;
    private final String issuer;

    public JwtUserValidator(GatewayAuthProperties.Jwt properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "me.gateway.auth.jwt.secret 未配置：user-validator=jwt 时必填，"
                            + "与下游 me.auth.jwt.secret 同一把不少于 256 位的随机密钥（配置中心统一下发）");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "me.gateway.auth.jwt.secret 强度不足：当前 " + keyBytes.length + " 字节，HS256 要求不少于 32 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = properties.getIssuer();
    }

    @Override
    public Mono<AuthIdentity> validate(String token) {
        return Mono.fromSupplier(() -> parse(token));
    }

    /**
     * 验签 + 校验 issuer/类型/时效，失败返回 {@code null}（由 filter 统一 401）.
     */
    private AuthIdentity parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!"access".equals(claims.get("type"))) {
                return null;
            }
            Object userId = claims.get("userId");
            if (userId == null) {
                return null;
            }
            Object account = claims.get("account");
            return new AuthIdentity(userId.toString(), account == null ? null : account.toString());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("用户 JWT 校验失败: {}", e.getMessage());
            return null;
        }
    }
}
