package com.frame.me.auth.core;

import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthUserAuthenticator} 单元测试.
 *
 * @author frame-me
 */
class AuthUserAuthenticatorTest {

    private final IAuthUserDetailsService userDetailsService = mock(IAuthUserDetailsService.class);
    private PasswordEncoder passwordEncoder;
    private AuthUserAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        authenticator = new AuthUserAuthenticator(passwordEncoder);
    }

    @Test
    void shouldReturnUserWhenPasswordMatches() {
        User user = new User();
        user.setId(1L);
        user.setAccount("admin");
        user.setPassword("hash");
        when(userDetailsService.loadUserByAccount("admin")).thenReturn(user);
        when(passwordEncoder.matches("raw", "hash")).thenReturn(true);

        User result = authenticator.authenticate(userDetailsService, "admin", "raw");

        assertThat(result).isSameAs(user);
    }

    @Test
    void shouldRejectWhenPasswordMismatch() {
        User user = new User();
        user.setPassword("hash");
        when(userDetailsService.loadUserByAccount("admin")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authenticator.authenticate(userDetailsService, "admin", "bad"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号或密码错误");
    }

    /**
     * 用户不存在时也必须执行一次 matches（对哑 hash），
     * 消除「不存在快速 401 / 存在慢速 401」的响应时间差.
     */
    @Test
    void shouldStillRunMatchesWithDummyHashWhenUserMissing() {
        when(userDetailsService.loadUserByAccount("ghost")).thenReturn(null);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authenticator.authenticate(userDetailsService, "ghost", "raw"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号或密码错误");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).matches(anyString(), hashCaptor.capture());
        assertThat(hashCaptor.getValue()).isNotBlank();
    }
}
