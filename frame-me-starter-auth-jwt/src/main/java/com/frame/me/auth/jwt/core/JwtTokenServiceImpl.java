package com.frame.me.auth.jwt.core;

import com.frame.me.auth.core.AuthUserAuthenticator;
import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import io.jsonwebtoken.*;
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
public class JwtTokenServiceImpl implements IAuthService {

    /**
     * 原始登录时间（Epoch 毫秒）claim，仅 Refresh Token 携带，续期时原样透传，
     * 供绝对寿命上限校验（签名保护，客户端无法篡改）.
     */
    static final String CLAIM_AUTH_TIME = "auth_time";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ACCOUNT = "account";
    private static final String CLAIM_TOKEN_TYPE = "type";
    /**
     * RP 快照：昵称 claim，仅 {@link #loginByUser}（SSO 下游等无本地用户表场景）签发的 token 携带.
     */
    private static final String CLAIM_NICKNAME = "nickname";
    /**
     * RP 快照标记 claim：存在即表示该 token 由 {@link #loginByUser} 签发，
     * {@code getUser}/{@code refresh} 在 {@code loadUserById} 返回 null 时允许从 claims 重建 User；
     * 密码登录签发的 token 无此标记，"删用户即时失效"语义不受影响.
     */
    private static final String CLAIM_RP_SNAPSHOT = "rp";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    /**
     * HMAC 密钥最低字节数（256 位），低于此值视为弱密钥拒绝启动.
     *
     * <p>HS256 的安全强度等于密钥长度，256 位（32 字节）为标准最低要求；
     * HS384/HS512 需更高，但绝大部分场景统一按 256 位 floor 校验即可，
     * 业务有更高要求时自行为 jjwt 传入自定义 {@code SecretKey}。</p>
     */
    private static final int MIN_KEY_BYTES = 32;
    private final JwtAuthProperties properties;
    private final IAuthUserDetailsService userDetailsService;
    private final IRefreshTokenStore refreshTokenStore;
    private final AuthUserAuthenticator authUserAuthenticator;

    /**
     * 启动期校验 {@code me.auth.jwt.secret}：未配置直接 fail-fast，
     * 并派生一次 {@link #secretKey} 把密钥强度问题（如 HS 系列弱密钥
     * {@code WeakKeyException}）从「首次请求才炸」提前到启动期暴露，同时缓存供后续签名/验签复用.
     * <p>
     * 签名密钥：启动期校验时派生并缓存，避免每请求重复 {@link Keys#hmacShaKeyFor} 派生.
     *
     * <p>可见性由 {@link #getSecretKey()} 的 {@code synchronized} 保证，
     * 无需 {@code volatile} 修饰.</p>
     */
    private SecretKey secretKey;

    @PostConstruct
    public void validateSecret() {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "me.auth.jwt.secret 未配置：JWT 签名密钥为必填项，请配置不少于 256 位的随机密钥");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "me.auth.jwt.secret 强度不足：当前 " + keyBytes.length + " 字节（"
                            + (keyBytes.length * 8) + " 位），HS256 要求不少于 256 位（32 字节），"
                            + "请用 openssl rand -base64 32 生成随机密钥");
        }
        secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private synchronized SecretKey getSecretKey() {
        // 惰性兜底：测试不经 Spring 生命周期（直接 new、未触发 @PostConstruct）时派生；
        // 生产环境由 validateSecret() 在启动期派生并缓存，此处直接命中缓存
        if (secretKey == null) {
            validateSecret();
            if (secretKey == null) {
                throw new IllegalStateException("JWT secret 未配置或密钥强度不足，拒绝派生");
            }
        }
        return secretKey;
    }

    @Override
    public String login(String account, String password) {
        User user = authUserAuthenticator.authenticate(userDetailsService, account, password);
        return buildTokenPair(user, System.currentTimeMillis());
    }

    /**
     * 按已知用户直接建立会话（RP 场景：身份已由外部 IdP 验证，无需密码校验）.
     *
     * <p>覆盖 {@link IAuthService#loginByUser}：供 SSO 下游等"code 换用户后建本地 session"
     * 场景使用。与 {@link #login} 的唯一区别是跳过 {@link AuthUserAuthenticator#authenticate}
     * 密码校验——身份已由外部 IdP 验证，直接 {@code buildTokenPair} 建会话。</p>
     *
     * <p><b>RP 快照：</b>RP 场景下游无本地用户表（{@code loadUserById} 恒 null），
     * 本方法签发的 token 额外写入 {@code rp}/{@code nickname} 快照 claims，
     * {@code getUser}/{@code refresh} 据此在 {@code loadUserById} miss 时从 claims 重建 User，
     * 否则 JWT 无状态下每个请求都会 401。</p>
     *
     * @param user 已认证用户（id 必填）
     * @return {@code accessToken;refreshToken}（分号分隔）
     */
    @Override
    public String loginByUser(User user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "用户信息无效");
        }
        if (!User.STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "账号已被禁用");
        }
        return buildTokenPair(user, System.currentTimeMillis(), true);
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
            refreshTokenStore.deleteUpstreamTokens(userId);
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
        refreshTokenStore.deleteUpstreamTokens(userId);
        log.debug("管理员强制登出用户，清除 Refresh Token: userId={}", userId);
    }

    /**
     * 留存上游 IdP token（RP 场景）.
     *
     * <p>JWT 无服务端 session，上游 token 落 {@link IRefreshTokenStore}（Redis/内存），
     * TTL 对齐 Refresh Token 时效——与本地会话同生共死，{@link #logout}/{@link #logoutByUserId}
     * 已同步清除。存超上游自身时效无害：消费方拿到过期 token 调上游接口自然 401。</p>
     */
    @Override
    public void storeUpstreamToken(Long userId, String appId, String upstreamToken) {
        if (userId == null || appId == null || appId.isBlank()
                || upstreamToken == null || upstreamToken.isBlank()) {
            return;
        }
        refreshTokenStore.saveUpstreamToken(userId, appId, upstreamToken, properties.getRefreshTokenExpires());
    }

    @Override
    public String getUpstreamToken(Long userId, String appId) {
        return userId == null || appId == null ? null : refreshTokenStore.getUpstreamToken(userId, appId);
    }

    @Override
    public String refresh(String credential) {
        String refreshToken = extractToken(credential);
        if (refreshToken == null) {
            // 凭证错误（4001）：refresh 流程本身失败，前端留登录页显示错误，避免与"会话缺失"401 混淆导致循环重定向
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "Refresh Token 无效");
        }
        try {
            Jws<Claims> jws = jwtParserBuilder()
                    .build()
                    .parseSignedClaims(refreshToken);
            Claims claims = jws.getPayload();
            if (!TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE))) {
                throw new BusinessException(ResultCode.BAD_CREDENTIAL, "Token 类型错误");
            }
            Object userIdClaim = claims.get(CLAIM_USER_ID);
            if (userIdClaim == null) {
                throw new BusinessException(ResultCode.BAD_CREDENTIAL, "Token 缺少用户标识");
            }
            Long userId = Long.valueOf(userIdClaim.toString());
            String cached = refreshTokenStore.get(userId);
            if (cached == null || !cached.equals(refreshToken)) {
                throw new BusinessException(ResultCode.BAD_CREDENTIAL, "Refresh Token 已失效");
            }
            User user = userDetailsService.loadUserById(userId);
            if (user == null) {
                // RP 回退：无本地用户表的 SSO 下游，用 loginByUser 签入的快照 claims 重建
                user = rebuildRpUser(claims);
            }
            if (user == null) {
                throw new BusinessException(ResultCode.BAD_CREDENTIAL, "用户不存在");
            }
            if (!User.STATUS_ENABLED.equals(user.getStatus())) {
                throw new BusinessException(ResultCode.BAD_CREDENTIAL, "账号已被禁用");
            }
            // 绝对寿命闸门：每次 refresh 都会签发新 Refresh Token 并重置完整有效期，
            // 不设上限时被偷的 Refresh Token 可无限链式续期；auth_time 随续期链路原样透传
            Object authTime = claims.get(CLAIM_AUTH_TIME);
            // ponytail: 存量 token 无 auth_time，按当前时间起算（一次宽限窗口）
            long authTimeMillis = authTime == null
                    ? System.currentTimeMillis() : Long.parseLong(authTime.toString());
            Duration maxLifetime = properties.getMaxLifetime();
            if (maxLifetime != null && !maxLifetime.isZero() && !maxLifetime.isNegative()
                    && System.currentTimeMillis() - authTimeMillis > maxLifetime.toMillis()) {
                throw new BusinessException(ResultCode.BAD_CREDENTIAL, "会话已达最长有效期，请重新登录");
            }
            // rp 快照随续期链路透传：否则续出的新 token 丢失快照，RP 下游下一请求即 401
            // 上游 token 条目同步续期：refresh 已把本地会话拉长（Refresh Token 重存完整 TTL），
            // 上游条目不续则第一个 refresh 周期后"同生共死"断裂；存超上游自身时效无害，SSO 侧 401 兜底
            refreshTokenStore.renewUpstreamTokens(userId, properties.getRefreshTokenExpires());
            TokenPair tokenPair = createTokenPair(
                    user, authTimeMillis, claims.get(CLAIM_RP_SNAPSHOT) != null);
            if (!refreshTokenStore.rotate(userId, refreshToken, tokenPair.refreshToken,
                    properties.getRefreshTokenExpires())) {
                throw new BusinessException(ResultCode.BAD_CREDENTIAL, "Refresh Token 已失效或已被使用");
            }
            return tokenPair.encoded();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "Refresh Token 已过期", e);
        } catch (JwtException | IllegalArgumentException e) {
            // 凭证本身的问题（格式非法/签名不符/类型错误）→ 4001
            log.warn("Refresh Token 解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.BAD_CREDENTIAL, "Refresh Token 无效", e);
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
        Claims claims = parseAccessClaims(credential);
        if (claims == null) {
            return null;
        }
        Long userId = extractAccessUserId(claims);
        if (userId == null) {
            return null;
        }
        User user = userDetailsService.loadUserById(userId);
        if (user == null) {
            // RP 回退：无本地用户表的 SSO 下游（loadUserById 恒 null），
            // 用 loginByUser 签入的快照 claims 重建 User；密码登录 token 无 rp 标记不触发
            user = rebuildRpUser(claims);
        }
        if (user != null && !User.STATUS_ENABLED.equals(user.getStatus())) {
            return null;
        }
        return user;
    }

    /**
     * 从 RP 快照 claims 重建 User（{@code loginByUser} 签发的 token 专用）.
     *
     * @param claims 已验签的 token claims
     * @return 含 {@code rp} 标记时返回重建的 User（status=ENABLED），否则 {@code null}
     */
    private User rebuildRpUser(Claims claims) {
        if (claims.get(CLAIM_RP_SNAPSHOT) == null) {
            return null;
        }
        User user = new User();
        Object userId = claims.get(CLAIM_USER_ID);
        user.setId(userId == null ? null : Long.valueOf(userId.toString()));
        Object account = claims.get(CLAIM_ACCOUNT);
        user.setAccount(account == null ? null : account.toString());
        Object nickname = claims.get(CLAIM_NICKNAME);
        user.setNickname(nickname == null ? null : nickname.toString());
        user.setStatus(User.STATUS_ENABLED);
        return user;
    }

    /**
     * 构建 Access Token + Refresh Token 对，以分号分隔.
     *
     * @param user           用户
     * @param authTimeMillis 原始登录时间（Epoch 毫秒），写入 Refresh Token 的
     *                       {@code auth_time} claim 并随续期链路透传
     * @return accessToken;refreshToken
     */
    private String buildTokenPair(User user, long authTimeMillis) {
        return buildTokenPair(user, authTimeMillis, false);
    }

    /**
     * 构建 Access Token + Refresh Token 对.
     *
     * @param rpSnapshot 是否写入 RP 快照 claims（{@code rp}/{@code nickname}）；
     *                   仅 {@code loginByUser}（SSO 下游无本地用户表）及 RP 续期链路透传为 true
     */
    private String buildTokenPair(User user, long authTimeMillis, boolean rpSnapshot) {
        TokenPair tokenPair = createTokenPair(user, authTimeMillis, rpSnapshot);
        refreshTokenStore.save(user.getId(), tokenPair.refreshToken, properties.getRefreshTokenExpires());
        return tokenPair.encoded();
    }

    /**
     * 仅生成 TokenPair，不写入 Refresh Token 存储；刷新链路用它先生成候选 token，
     * 再通过 {@link IRefreshTokenStore#rotate} 原子提交，避免并发重复消费旧 token.
     */
    private TokenPair createTokenPair(User user, long authTimeMillis, boolean rpSnapshot) {
        String accessToken = buildToken(user, TOKEN_TYPE_ACCESS, properties.getAccessTokenExpires(),
                null, rpSnapshot);
        String refreshToken = buildToken(user, TOKEN_TYPE_REFRESH, properties.getRefreshTokenExpires(),
                authTimeMillis, rpSnapshot);
        return new TokenPair(accessToken, refreshToken);
    }

    private static final class TokenPair {

        private final String accessToken;
        private final String refreshToken;

        private TokenPair(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        private String encoded() {
            return accessToken + ";" + refreshToken;
        }
    }

    /**
     * 构建单个 JWT.
     *
     * @param authTimeMillis 原始登录时间（Epoch 毫秒），非 null 时写入 {@code auth_time} claim
     * @param rpSnapshot     是否写入 RP 快照 claims
     */
    private String buildToken(User user, String type, Duration expires, Long authTimeMillis, boolean rpSnapshot) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expires.toMillis());
        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ACCOUNT, user.getAccount())
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(expiration)
                .id(UUID.randomUUID().toString());
        if (authTimeMillis != null) {
            builder.claim(CLAIM_AUTH_TIME, authTimeMillis);
        }
        if (rpSnapshot) {
            builder.claim(CLAIM_RP_SNAPSHOT, true);
            if (user.getNickname() != null) {
                builder.claim(CLAIM_NICKNAME, user.getNickname());
            }
        }
        return builder.signWith(getSecretKey()).compact();
    }

    /**
     * 从凭证中提取 Access Token 并解析为用户 ID.
     */
    private Long parseAccessToken(String credential) {
        Claims claims = parseAccessClaims(credential);
        return claims == null ? null : extractAccessUserId(claims);
    }

    /**
     * 从凭证中提取 Access Token 并解析为已验签 claims（过期/非法返回 null）.
     */
    private Claims parseAccessClaims(String credential) {
        String token = extractToken(credential);
        if (token == null) {
            return null;
        }
        try {
            Jws<Claims> jws = jwtParserBuilder()
                    .build()
                    .parseSignedClaims(token);
            return jws.getPayload();
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
            log.debug("Refresh Token 解析失败: {}", e.getMessage(), e);
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
