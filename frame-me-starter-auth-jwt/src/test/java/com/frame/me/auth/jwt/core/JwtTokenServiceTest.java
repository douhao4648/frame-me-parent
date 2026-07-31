package com.frame.me.auth.jwt.core;

import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.auth.util.PasswordUtils;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtTokenService} 单元测试.
 *
 * @author frame-me
 */
class JwtTokenServiceTest {

    private static final String SECRET = "frame-me-jwt-secret-key-at-least-32-characters-long";

    private JwtTokenService tokenService;

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

        tokenService = new JwtTokenService(properties, userDetailsService, new InMemoryRefreshTokenStore());
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
        JwtTokenService noSecret = new JwtTokenService(properties, null, null);
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
        JwtTokenService weakSecret = new JwtTokenService(properties, null, null);
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
        JwtTokenService otherService = new JwtTokenService(otherProps, tokenService.getClass() != null
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
        JwtTokenService serviceWithFailingStore = new JwtTokenService(properties, null, failingStore);

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
        JwtTokenService expiredService = new JwtTokenService(expiredProps,
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
        JwtTokenService expiredService = new JwtTokenService(expiredProps,
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
