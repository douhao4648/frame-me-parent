package com.frame.me.auth.jwt.core;

import com.frame.me.auth.core.AuthUserAuthenticator;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import com.frame.me.auth.jwt.config.JwtAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 认证服务实现.
 *
 * @author frame-me
 */
@Slf4j
@RequiredArgsConstructor
public class JwtTokenService implements IAuthService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ACCOUNT = "account";
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtAuthProperties properties;
    private final IAuthUserDetailsService userDetailsService;
    private final RefreshTokenStore refreshTokenStore;

    @Override
    public String login(String account, String password) {
        User user = AuthUserAuthenticator.authenticate(userDetailsService, account, password);
        return buildTokenPair(user);
    }

    @Override
    public void logout(String credential) {
        Long userId = parseAccessToken(credential);
        if (userId == null) {
            // Access Token 过期或无效时，尝试按 Refresh Token 清除
            userId = parseRefreshToken(credential);
        }
        if (userId != null) {
            refreshTokenStore.delete(userId);
            log.debug("用户登出，清除 Refresh Token: userId={}", userId);
        }
    }

    /**
     * 按用户 ID 强制登出：清除该用户的 Refresh Token.
     *
     * <p>已颁发的 Access Token 在自然过期前仍然有效（JWT 无状态限制）。</p>
     */
    @Override
    public void logoutByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        refreshTokenStore.delete(userId);
        log.debug("管理员强制登出用户，清除 Refresh Token: userId={}", userId);
    }

    @Override
    public String refresh(String credential) {
        String refreshToken = extractToken(credential);
        if (refreshToken == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Refresh Token 无效");
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(refreshToken);
            Claims claims = jws.getPayload();
            if (!TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE))) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "Token 类型错误");
            }
            Long userId = Long.valueOf(claims.get(CLAIM_USER_ID).toString());
            String cached = refreshTokenStore.get(userId);
            if (cached == null || !cached.equals(refreshToken)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "Refresh Token 已失效");
            }
            User user = userDetailsService.loadUserById(userId);
            if (user == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
            }
            return buildTokenPair(user);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Refresh Token 已过期");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Refresh Token 解析失败", e);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Refresh Token 无效");
        }
    }

    @Override
    public boolean validate(String credential) {
        return parseAccessToken(credential) != null;
    }

    @Override
    public User getUser(String credential) {
        Long userId = parseAccessToken(credential);
        if (userId == null) {
            return null;
        }
        return userDetailsService.loadUserById(userId);
    }

    /**
     * 构建 Access Token + Refresh Token 对，以分号分隔.
     *
     * @param user 用户
     * @return accessToken;refreshToken
     */
    private String buildTokenPair(User user) {
        String accessToken = buildToken(user, TOKEN_TYPE_ACCESS, properties.getAccessTokenExpires());
        String refreshToken = buildToken(user, TOKEN_TYPE_REFRESH, properties.getRefreshTokenExpires());
        refreshTokenStore.save(user.getId(), refreshToken, properties.getRefreshTokenExpires());
        return accessToken + ";" + refreshToken;
    }

    /**
     * 构建单个 JWT.
     */
    private String buildToken(User user, String type, Duration expires) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expires.toMillis());
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ACCOUNT, user.getAccount())
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(expiration)
                .id(UUID.randomUUID().toString())
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 从凭证中提取 Access Token 并解析为用户 ID.
     */
    private Long parseAccessToken(String credential) {
        String token = extractToken(credential);
        if (token == null) {
            return null;
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            if (!TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE))) {
                return null;
            }
            return Long.valueOf(claims.get(CLAIM_USER_ID).toString());
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException
                 | SignatureException | IllegalArgumentException e) {
            log.debug("Access Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从凭证中提取 Refresh Token 并解析为用户 ID.
     */
    private Long parseRefreshToken(String credential) {
        String token = extractToken(credential);
        if (token == null) {
            return null;
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            if (!TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE))) {
                return null;
            }
            return Long.valueOf(claims.get(CLAIM_USER_ID).toString());
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException
                 | SignatureException | IllegalArgumentException e) {
            log.debug("Refresh Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从凭证字符串中提取 Token（去除 Bearer 前缀）.
     */
    private String extractToken(String credential) {
        if (credential == null || credential.isEmpty()) {
            return null;
        }
        String prefix = properties.getTokenPrefix();
        if (credential.startsWith(prefix)) {
            return credential.substring(prefix.length()).trim();
        }
        return credential;
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
