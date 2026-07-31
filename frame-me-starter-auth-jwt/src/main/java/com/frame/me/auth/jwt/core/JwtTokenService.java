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
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
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
    private final IRefreshTokenStore refreshTokenStore;

    /**
     * 签名密钥：启动期校验时派生并缓存，避免每请求重复 {@link Keys#hmacShaKeyFor} 派生.
     */
    private volatile SecretKey secretKey;

    /**
     * 启动期校验 {@code me.auth.jwt.secret}：未配置直接 fail-fast，
     * 并派生一次 {@link #secretKey} 把密钥强度问题（如 HS 系列弱密钥
     * {@code WeakKeyException}）从「首次请求才炸」提前到启动期暴露，同时缓存供后续签名/验签复用.
     */
    @PostConstruct
    public void validateSecret() {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "me.auth.jwt.secret 未配置：JWT 签名密钥为必填项，请配置不少于 256 位的随机密钥");
        }
        secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getSecretKey() {
        // 惰性兜底：测试不经 Spring 生命周期（直接 new、未触发 @PostConstruct）时派生；
        // 生产环境由 validateSecret() 在启动期派生并缓存，此处直接命中缓存
        if (secretKey == null) {
            secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        }
        return secretKey;
    }

    @Override
    public String login(String account, String password) {
        User user = AuthUserAuthenticator.authenticate(userDetailsService, account, password);
        return buildTokenPair(user);
    }

    @Override
    public void logout(String credential) {
        Long userId = parseAccessTokenForLogout(credential);
        if (userId == null) {
            // Access Token 无效时，尝试按 Refresh Token 清除（同样接受过期 token）
            userId = parseRefreshTokenForLogout(credential);
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
            Jws<Claims> jws = jwtParserBuilder()
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
        } catch (JwtException | IllegalArgumentException e) {
            // 凭证本身的问题（格式非法/签名不符/类型错误）→ 401
            log.warn("Refresh Token 解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Refresh Token 无效");
        }
        // 其余异常（如 refreshTokenStore 的 Redis 故障）属基础设施问题，
        // 不吞成 401（客户端会误以为凭证失效而走重新登录），直接上抛由全局异常处理映射 5xx
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
            Jws<Claims> jws = jwtParserBuilder()
                    .build()
                    .parseSignedClaims(token);
            return extractAccessUserId(jws.getPayload());
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException
                 | IncorrectClaimException | SignatureException | IllegalArgumentException e) {
            log.debug("Access Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 登出场景解析 Access Token：过期 token 同样接受.
     *
     * <p>jjwt 先验签再校验时效，{@link ExpiredJwtException} 携带的是已验签的 claims，
     * 登出（撤销）场景可以安全使用其中的用户 ID，避免 token 过期后登出静默失效、
     * Refresh Token 残留。仅限 logout 使用，不影响 validate / getUser 的时效语义。</p>
     */
    private Long parseAccessTokenForLogout(String credential) {
        String token = extractToken(credential);
        if (token == null) {
            return null;
        }
        try {
            Jws<Claims> jws = jwtParserBuilder()
                    .build()
                    .parseSignedClaims(token);
            return extractAccessUserId(jws.getPayload());
        } catch (ExpiredJwtException e) {
            return extractAccessUserId(e.getClaims());
        } catch (UnsupportedJwtException | MalformedJwtException
                 | IncorrectClaimException | SignatureException | IllegalArgumentException e) {
            log.debug("Access Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 claims 中提取 Access Token 的用户 ID，类型不符或 claim 缺失返回 {@code null}.
     *
     * <p>{@code userId} claim 缺失时（token 被篡改/构造异常）返回 {@code null}，
     * 由调用方走 401，而非抛 NPE 逃逸成 500.</p>
     */
    private Long extractAccessUserId(Claims claims) {
        if (!TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE))) {
            return null;
        }
        Object userId = claims.get(CLAIM_USER_ID);
        if (userId == null) {
            return null;
        }
        return Long.valueOf(userId.toString());
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
            Jws<Claims> jws = jwtParserBuilder()
                    .build()
                    .parseSignedClaims(token);
            return extractRefreshUserId(jws.getPayload());
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException
                 | IncorrectClaimException | SignatureException | IllegalArgumentException e) {
            log.debug("Refresh Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 登出场景解析 Refresh Token：过期 token 同样接受（同 access 的登出放宽逻辑）.
     */
    private Long parseRefreshTokenForLogout(String credential) {
        String token = extractToken(credential);
        if (token == null) {
            return null;
        }
        try {
            Jws<Claims> jws = jwtParserBuilder()
                    .build()
                    .parseSignedClaims(token);
            return extractRefreshUserId(jws.getPayload());
        } catch (ExpiredJwtException e) {
            return extractRefreshUserId(e.getClaims());
        } catch (UnsupportedJwtException | MalformedJwtException
                 | IncorrectClaimException | SignatureException | IllegalArgumentException e) {
            log.debug("Refresh Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 claims 中提取 Refresh Token 的用户 ID，类型不符或 claim 缺失返回 {@code null}.
     */
    private Long extractRefreshUserId(Claims claims) {
        if (!TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE))) {
            return null;
        }
        Object userId = claims.get(CLAIM_USER_ID);
        if (userId == null) {
            return null;
        }
        return Long.valueOf(userId.toString());
    }

    /**
     * 从凭证字符串中提取 Token（去除 Bearer 前缀）.
     *
     * <p>按 RFC 6750 §2.1，Bearer 前缀大小写不敏感（{@code Bearer} / {@code bearer} / {@code BEARER}
     * 均应接受）。旧实现用 {@code startsWith} 大小写敏感，客户端发小写前缀会匹配失败、
     * 原样返回带前缀的字符串导致 jjwt 解析失败。</p>
     */
    private String extractToken(String credential) {
        if (credential == null || credential.isEmpty()) {
            return null;
        }
        String prefix = properties.getTokenPrefix();
        if (prefix != null && !prefix.isEmpty()
                && credential.length() >= prefix.length()
                && credential.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return credential.substring(prefix.length()).trim();
        }
        return credential;
    }

    /**
     * 构建 JWT parser：验签 + 校验 issuer.
     *
     * <p>签发端写入 {@code iss} claim（{@link #buildToken}），解析端必须用
     * {@code requireIssuer} 校验，否则 issuer claim 形同虚设——同 secret 跨服务/
     * 跨环境签发的 token 会互相通过验证。5 处解析统一走本方法，避免重复.</p>
     *
     * <p>issuer 不匹配抛 {@link IncorrectClaimException}（{@link JwtException} 子类），
     * 各调用点的 catch 已覆盖.</p>
     */
    private JwtParserBuilder jwtParserBuilder() {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .requireIssuer(properties.getIssuer());
    }
}
