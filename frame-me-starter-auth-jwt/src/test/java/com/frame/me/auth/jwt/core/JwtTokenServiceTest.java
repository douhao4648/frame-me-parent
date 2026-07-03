package com.frame.me.auth.jwt.core;

import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.jwt.util.PasswordUtils;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
