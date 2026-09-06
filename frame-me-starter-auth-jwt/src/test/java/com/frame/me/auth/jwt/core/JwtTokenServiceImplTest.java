package com.frame.me.auth.jwt.core;

import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.auth.util.PasswordUtils;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.user.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtTokenServiceImpl} 单元测试.
 *
 * @author frame-me
 */
class JwtTokenServiceImplTest {

    private static final String SECRET = "frame-me-jwt-secret-key-at-least-32-characters-long";

    private JwtTokenServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        JwtAuthProperties properties = new JwtAuthProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenExpires(Duration.ofMinutes(10));
        properties.setRefreshTokenExpires(Duration.ofMinutes(30));

        IAuthUserDetailsService userDetailsService = new IAuthUserDetailsService() {
            @Override
            public User loadUserByAccount(String account) {
                if (!"admin".equals(account)) {
                    return null;
                }
                User user = new User();
                user.setId(1L);
                user.setAccount(account);
                user.setPassword(PasswordUtils.encode("123456"));
                return user;
            }

            @Override
            public User loadUserById(Long id) {
                if (!Long.valueOf(1L).equals(id)) {
                    return null;
                }
                User user = new User();
                user.setId(id);
                user.setAccount("admin");
                user.setPassword(PasswordUtils.encode("123456"));
                return user;
            }

            @Override
            public boolean matches(String rawPassword, String encodedPassword) {
                return PasswordUtils.matches(rawPassword, encodedPassword);
            }
        };

        tokenService = new JwtTokenServiceImpl(properties, userDetailsService, new InMemoryRefreshTokenStore());
    }

    @Test
    void testLoginSuccess() {
        String tokenPair = tokenService.login("admin", "123456");
        assertNotNull(tokenPair);
        String[] parts = tokenPair.split(";");
        assertEquals(2, parts.length);
        assertTrue(tokenService.validate(parts[0]));
        User user = tokenService.getUser(parts[0]);
        assertNotNull(user);
        assertEquals(1L, user.getId());
        assertEquals("admin", user.getAccount());
    }

    /**
     * secret 未配置/为空白时启动期 fail-fast，而不是首次请求才 NPE.
     */
    @Test
    void testBlankSecretFailsFast() {
        JwtAuthProperties properties = new JwtAuthProperties();
        JwtTokenServiceImpl noSecret = new JwtTokenServiceImpl(properties, null, null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, noSecret::validateSecret);
        assertTrue(ex.getMessage().contains("me.auth.jwt.secret"), ex.getMessage());

        properties.setSecret("   ");
        assertThrows(IllegalStateException.class, noSecret::validateSecret, "纯空白 secret 同样 fail-fast");
    }

    /**
     * 弱密钥（长度不足 256 位/32 字节）在启动期校验即抛出，而不是首次签名才暴露.
     *
     * <p>新版校验在 {@code Keys.hmacShaKeyFor} 之前先做字节数下限校验，
     * 抛 {@link IllegalStateException}（而非 {@code WeakKeyException}），
     * 同时覆盖 jjwt 原生弱密钥检查和更短密钥的场景.</p>
     */
    @Test
    void testWeakSecretFailsFast() {
        JwtAuthProperties properties = new JwtAuthProperties();
        properties.setSecret("too-short");
        JwtTokenServiceImpl weakSecret = new JwtTokenServiceImpl(properties, null, null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, weakSecret::validateSecret);
        assertTrue(ex.getMessage().contains("强度不足"), ex.getMessage());
    }

    @Test
    void testLoginFailure() {
        assertThrows(BusinessException.class, () -> tokenService.login("admin", "wrong"));
        assertThrows(BusinessException.class, () -> tokenService.login("not-exist", "123456"));
    }

    @Test
    void testRefresh() {
        String tokenPair = tokenService.login("admin", "123456");
        String refreshToken = tokenPair.split(";")[1];

        String newPair = tokenService.refresh(refreshToken);
        String[] parts = newPair.split(";");
        assertEquals(2, parts.length);
        assertTrue(tokenService.validate(parts[0]));
    }

    /**
     * 绝对寿命闸门：{@code auth_time} 距现在超过 max-lifetime（默认 30 天）的
     * Refresh Token 续期被拒绝（4001），防止续期链路无限延长.
     */
    @Test
    void testRefreshBeyondMaxLifetimeRejected() {
        JwtAuthProperties props = new JwtAuthProperties();
        props.setSecret(SECRET);
        props.setRefreshTokenExpires(Duration.ofDays(7));
        InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();
        JwtTokenServiceImpl service = new JwtTokenServiceImpl(props, new IAuthUserDetailsService() {
            @Override
            public User loadUserByAccount(String account) {
                return null;
            }

            @Override
            public User loadUserById(Long id) {
                User user = new User();
                user.setId(id);
                user.setAccount("admin");
                return user;
            }

            @Override
            public boolean matches(String rawPassword, String encodedPassword) {
                return false;
            }
        }, store);

        // 手动构造 auth_time 为 40 天前、自身未过期的 Refresh Token（默认上限 30 天）
        Date now = new Date();
        String staleRefreshToken = Jwts.builder()
                .subject("1")
                .claim("userId", 1L)
                .claim("type", "refresh")
                .claim(JwtTokenServiceImpl.CLAIM_AUTH_TIME,
                        now.getTime() - 40L * 24 * 3600 * 1000)
                .issuer(props.getIssuer())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 7L * 24 * 3600 * 1000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        store.save(1L, staleRefreshToken, Duration.ofDays(7));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.refresh(staleRefreshToken));
        assertTrue(ex.getMessage().contains("最长有效期"), ex.getMessage());
    }

    /**
     * RFC 6750 §2.1：Bearer 前缀大小写不敏感。
     * {@code Bearer}/{@code bearer}/{@code BEARER} 前缀都应正确剥离后通过校验。
     */
    @Test
    void testBearerPrefixCaseInsensitive() {
        String tokenPair = tokenService.login("admin", "123456");
        String accessToken = tokenPair.split(";")[0];

        // 大写（默认）、小写、全大写前缀均应剥离成功并校验通过
        assertTrue(tokenService.validate("Bearer " + accessToken));
        assertTrue(tokenService.validate("bearer " + accessToken));
        assertTrue(tokenService.validate("BEARER " + accessToken));

        User user = tokenService.getUser("bearer " + accessToken);
        assertNotNull(user);
        assertEquals(1L, user.getId());
    }

    /**
     * 无前缀的裸 token 仍应通过校验（容错）.
     */
    @Test
    void testBareTokenStillValid() {
        String tokenPair = tokenService.login("admin", "123456");
        String accessToken = tokenPair.split(";")[0];

        assertTrue(tokenService.validate(accessToken));
    }

    /**
     * 签发端设 issuer（默认 {@code me}），解析端校验 issuer：
     * 默认实例签发的 token（iss=me）在默认实例校验通过.
     */
    @Test
    void testDefaultIssuerValidated() {
        String tokenPair = tokenService.login("admin", "123456");
        String accessToken = tokenPair.split(";")[0];

        // 默认 issuer="me"，签发与解析同一实例，应通过
        assertTrue(tokenService.validate(accessToken));
    }

    /**
     * issuer 不匹配的 token 应被拒：用 issuer=other 签发，默认实例（issuer=me）解析应失败.
     *
     * <p>防护场景：多服务/多环境共用同一 secret 时，A 签发的 token 不应在 B 通过校验。
     * 旧实现解析端不校验 iss，跨签发者 token 可穿透.</p>
     */
    @Test
    void testMismatchedIssuerRejected() {
        // 用不同 issuer 签发 token（共用同一 secret 与 userDetailsService）
        JwtAuthProperties otherProps = new JwtAuthProperties();
        otherProps.setSecret(SECRET);
        otherProps.setAccessTokenExpires(Duration.ofMinutes(10));
        otherProps.setRefreshTokenExpires(Duration.ofMinutes(30));
        otherProps.setIssuer("other-issuer");
        JwtTokenServiceImpl otherService = new JwtTokenServiceImpl(otherProps, tokenService.getClass() != null
                ? new IAuthUserDetailsService() {
                    @Override
                    public User loadUserByAccount(String account) {
                        User u = new User();
                        u.setId(1L);
                        u.setAccount(account);
                        return u;
                    }

                    @Override
                    public User loadUserById(Long id) {
                        User u = new User();
                        u.setId(id);
                        u.setAccount("admin");
                        return u;
                    }

                    @Override
                    public boolean matches(String rawPassword, String encodedPassword) {
                        return true;
                    }
                } : null, new InMemoryRefreshTokenStore());

        String otherToken = otherService.login("admin", "123456").split(";")[0];

        // 默认实例（issuer=me）解析 other-issuer 签发的 token 应失败（validate 返回 false）
        assertFalse(tokenService.validate(otherToken));
        assertNull(tokenService.getUser(otherToken));
    }

    /**
     * refreshTokenStore 基础设施故障（如 Redis 连接异常）必须原样上抛走 5xx，
     * 不得被吞成 401「Refresh Token 无效」误导客户端走重新登录.
     */
    @Test
    void testRefreshStoreFailurePropagates() {
        IRefreshTokenStore failingStore = new InMemoryRefreshTokenStore() {
            @Override
            public String get(Long userId) {
                throw new IllegalStateException("redis down");
            }
        };
        JwtAuthProperties properties = new JwtAuthProperties();
        properties.setSecret(SECRET);
        JwtTokenServiceImpl serviceWithFailingStore = new JwtTokenServiceImpl(properties, null, failingStore);

        // 用同密钥的正常服务签发合法 Refresh Token，故障服务解析通过后 get 抛基础设施异常
        String tokenPair = tokenService.login("admin", "123456");
        String refreshToken = tokenPair.split(";")[1];

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> serviceWithFailingStore.refresh(refreshToken));
        assertEquals("redis down", ex.getMessage());
    }

    @Test
    void testLogout() {
        String tokenPair = tokenService.login("admin", "123456");
        String accessToken = tokenPair.split(";")[0];
        String refreshToken = tokenPair.split(";")[1];

        tokenService.logout(accessToken);

        // Access Token 本身仍然有效到过期，但 Refresh Token 已失效
        assertTrue(tokenService.validate(accessToken));
        assertThrows(BusinessException.class, () -> tokenService.refresh(refreshToken));
    }

    /**
     * 上游 IdP token（RP 留存）：按 appId 隔离存取（不同应用互不覆盖、按 app 取不错串）；
     * logout / logoutByUserId 同步清除全部应用；空入参 no-op.
     */
    @Test
    void testUpstreamToken_storeGetAndClearedOnLogout() {
        tokenService.storeUpstreamToken(1L, "fm-audit", "sso-token-audit");
        tokenService.storeUpstreamToken(1L, "fm-order", "sso-token-order");
        assertEquals("sso-token-audit", tokenService.getUpstreamToken(1L, "fm-audit"));
        assertEquals("sso-token-order", tokenService.getUpstreamToken(1L, "fm-order"));
        assertNull(tokenService.getUpstreamToken(1L, "fm-other"));

        String accessToken = tokenService.login("admin", "123456").split(";")[0];
        tokenService.logout(accessToken);
        assertNull(tokenService.getUpstreamToken(1L, "fm-audit"));
        assertNull(tokenService.getUpstreamToken(1L, "fm-order"));

        // logoutByUserId 同样清除
        tokenService.storeUpstreamToken(1L, "fm-audit", "sso-token-def");
        tokenService.logoutByUserId(1L);
        assertNull(tokenService.getUpstreamToken(1L, "fm-audit"));

        // 空入参静默忽略
        tokenService.storeUpstreamToken(null, "fm-audit", "x");
        tokenService.storeUpstreamToken(1L, null, "x");
        tokenService.storeUpstreamToken(1L, "fm-audit", null);
        assertNull(tokenService.getUpstreamToken(null, "fm-audit"));
        assertNull(tokenService.getUpstreamToken(1L, null));
        assertNull(tokenService.getUpstreamToken(1L, "fm-audit"));
    }

    /**
     * 上游 token 随 refresh 续期：本地会话经 refresh 拉长后，上游条目不能在第一个
     * TTL 窗口后就消失（"同生共死"语义）.
     */
    @Test
    void testUpstreamToken_renewedOnRefresh() throws InterruptedException {
        JwtAuthProperties shortProps = new JwtAuthProperties();
        shortProps.setSecret(SECRET);
        shortProps.setAccessTokenExpires(Duration.ofMinutes(10));
        shortProps.setRefreshTokenExpires(Duration.ofMillis(4000));
        JwtTokenServiceImpl shortLived = new JwtTokenServiceImpl(shortProps, new IAuthUserDetailsService() {
            @Override
            public User loadUserByAccount(String account) {
                return null;
            }

            @Override
            public User loadUserById(Long id) {
                return null;
            }
        }, new InMemoryRefreshTokenStore());

        // RP 登录（loadUserById 恒 null，refresh 走 rp 快照重建链路）
        User rpUser = new User();
        rpUser.setId(1L);
        rpUser.setAccount("admin");
        rpUser.setStatus(User.STATUS_ENABLED);
        String refreshToken = shortLived.loginByUser(rpUser).split(";")[1];
        shortLived.storeUpstreamToken(1L, "fm-audit", "sso-token-abc");

        // 时序余量按 CI/全量构建高负载放宽（曾两次因 sleep 超调 257ms 抖动失败）：
        // t=2000ms：原 TTL（4000ms）过半，refresh 成功并把上游条目续到 t=6000ms
        Thread.sleep(2000);
        shortLived.refresh(refreshToken);
        // t=4400ms：已过原 TTL，未续期的话条目已消失
        Thread.sleep(2400);
        assertEquals("sso-token-abc", shortLived.getUpstreamToken(1L, "fm-audit"));
    }

    @Test
    void testLogout_withRefreshTokenAlsoClearsStore() {
        String tokenPair = tokenService.login("admin", "123456");
        String accessToken = tokenPair.split(";")[0];
        String refreshToken = tokenPair.split(";")[1];

        // 直接用 Refresh Token 调用 logout 也能清除 Refresh Token
        tokenService.logout(refreshToken);

        assertTrue(tokenService.validate(accessToken));
        assertThrows(BusinessException.class, () -> tokenService.refresh(refreshToken));
    }

    /**
     * Access Token 已过期时登出不能静默失效：过期 token 的 claims 已经验签，
     * 登出场景应从中取用户 ID 并清除 Refresh Token.
     */
    @Test
    void testLogout_withExpiredAccessTokenStillClearsStore() {
        // 签发一个已过期的 Access Token（有效期设为负数）
        JwtAuthProperties expiredProps = new JwtAuthProperties();
        expiredProps.setSecret(SECRET);
        expiredProps.setAccessTokenExpires(Duration.ofSeconds(-60));
        expiredProps.setRefreshTokenExpires(Duration.ofMinutes(30));
        InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();
        JwtTokenServiceImpl expiredService = new JwtTokenServiceImpl(expiredProps,
                new IAuthUserDetailsService() {
                    @Override
                    public User loadUserByAccount(String account) {
                        User user = new User();
                        user.setId(1L);
                        user.setAccount(account);
                        user.setPassword(PasswordUtils.encode("123456"));
                        return user;
                    }

                    @Override
                    public User loadUserById(Long id) {
                        return null;
                    }

                    @Override
                    public boolean matches(String rawPassword, String encodedPassword) {
                        return PasswordUtils.matches(rawPassword, encodedPassword);
                    }
                }, store);

        String tokenPair = expiredService.login("admin", "123456");
        String expiredAccessToken = tokenPair.split(";")[0];
        String refreshToken = tokenPair.split(";")[1];

        // 过期 Access Token 不再通过校验，但登出仍应清除 Refresh Token
        expiredService.logout(expiredAccessToken);
        assertThrows(BusinessException.class, () -> expiredService.refresh(refreshToken));
    }

    /**
     * Refresh Token 已过期但存储中仍有残留（如自定义存储 TTL 不同步）时，
     * 用过期 Refresh Token 登出同样应清除存储.
     */
    @Test
    void testLogout_withExpiredRefreshTokenStillClearsStore() {
        JwtAuthProperties expiredProps = new JwtAuthProperties();
        expiredProps.setSecret(SECRET);
        expiredProps.setAccessTokenExpires(Duration.ofMinutes(10));
        expiredProps.setRefreshTokenExpires(Duration.ofSeconds(-60));
        // 不过期的简易存储：模拟 TTL 与 token 有效期不同步导致的残留
        LingeringRefreshTokenStore store = new LingeringRefreshTokenStore();
        JwtTokenServiceImpl expiredService = new JwtTokenServiceImpl(expiredProps,
                new IAuthUserDetailsService() {
                    @Override
                    public User loadUserByAccount(String account) {
                        User user = new User();
                        user.setId(1L);
                        user.setAccount(account);
                        user.setPassword(PasswordUtils.encode("123456"));
                        return user;
                    }

                    @Override
                    public User loadUserById(Long id) {
                        return null;
                    }

                    @Override
                    public boolean matches(String rawPassword, String encodedPassword) {
                        return PasswordUtils.matches(rawPassword, encodedPassword);
                    }
                }, store);

        String tokenPair = expiredService.login("admin", "123456");
        String expiredRefreshToken = tokenPair.split(";")[1];
        assertNotNull(store.get(1L));

        expiredService.logout(expiredRefreshToken);

        assertNull(store.get(1L));
    }

    /**
     * RP 快照回退：无本地用户表的 SSO 下游（loadUserById 恒 null），
     * {@code loginByUser} 签发的 token 携带 rp 快照 claims，getUser 从 claims 重建 User，
     * 否则 JWT 无状态下每个请求都会 401.
     */
    @Test
    void testRpFallback_getUserRebuildsFromClaims() {
        JwtTokenServiceImpl rpService = rpService();
        User rpUser = new User();
        rpUser.setId(443549360765247488L);
        rpUser.setAccount("cuijiji");
        rpUser.setNickname("崔机机");
        rpUser.setStatus(User.STATUS_ENABLED);

        String accessToken = rpService.loginByUser(rpUser).split(";")[0];

        User resolved = rpService.getUser(accessToken);
        assertNotNull(resolved, "RP token 的 getUser 应从 claims 快照重建 User");
        assertEquals(443549360765247488L, resolved.getId());
        assertEquals("cuijiji", resolved.getAccount());
        assertEquals("崔机机", resolved.getNickname());
        assertEquals(User.STATUS_ENABLED, resolved.getStatus());
    }

    /**
     * RP 续期链路：refresh 同样走快照回退，且续出的新 token 保留 rp 快照，
     * 否则下一轮 getUser 立即 401.
     */
    @Test
    void testRpFallback_refreshKeepsSnapshot() {
        JwtTokenServiceImpl rpService = rpService();
        User rpUser = new User();
        rpUser.setId(99L);
        rpUser.setAccount("rp-user");
        rpUser.setStatus(User.STATUS_ENABLED);

        String refreshToken = rpService.loginByUser(rpUser).split(";")[1];
        String newAccessToken = rpService.refresh(refreshToken).split(";")[0];

        User resolved = rpService.getUser(newAccessToken);
        assertNotNull(resolved, "续期后的新 token 应保留 rp 快照");
        assertEquals(99L, resolved.getId());
        assertEquals("rp-user", resolved.getAccount());
    }

    /**
     * 快照回退的边界：密码登录签发的 token 无 rp 标记，loadUserById 返回 null
     * （用户已删除）时 getUser 必须返回 null——"删用户即时失效"语义不被回退稀释.
     */
    @Test
    void testRpFallback_passwordLoginTokenNotAffected() {
        JwtTokenServiceImpl rpService = rpService();
        // 手工签发无 rp 标记的 token（等价于密码登录签发的 token 遇上用户被删）
        Date now = new Date();
        String plainToken = Jwts.builder()
                .subject("1")
                .claim("userId", 1L)
                .claim("account", "admin")
                .claim("type", "access")
                .issuer("me")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertNull(rpService.getUser(plainToken), "无 rp 标记的 token 不允许快照回退");
    }

    /**
     * 构造 RP 场景服务：loadUserById 恒返回 null（无本地用户表）.
     */
    private JwtTokenServiceImpl rpService() {
        JwtAuthProperties properties = new JwtAuthProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenExpires(Duration.ofMinutes(10));
        properties.setRefreshTokenExpires(Duration.ofMinutes(30));
        return new JwtTokenServiceImpl(properties, new IAuthUserDetailsService() {
            @Override
            public User loadUserByAccount(String account) {
                return null;
            }

            @Override
            public User loadUserById(Long id) {
                return null;
            }
        }, new InMemoryRefreshTokenStore());
    }

    /**
     * 无 TTL 的简易存储，模拟过期条目残留场景.
     */
    private static class LingeringRefreshTokenStore implements IRefreshTokenStore {

        private final Map<Long, String> store = new HashMap<>();

        @Override
        public void save(Long userId, String refreshToken, Duration expires) {
            store.put(userId, refreshToken);
        }

        @Override
        public String get(Long userId) {
            return store.get(userId);
        }

        @Override
        public void delete(Long userId) {
            store.remove(userId);
        }
    }
}
