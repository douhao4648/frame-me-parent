package com.frame.me.sso.auth;

import com.frame.me.base.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SsoAuthUserDetailsServiceImpl} 单元测试：薄委托层——loadUserById 委托
 * {@link SsoAuthService#loadUserByUpstreamToken}，loadUserByAccount 恒 null.
 *
 * @author frame-me
 */
class SsoAuthUserDetailsServiceImplTest {

    private final SsoAuthService ssoAuthService = mock(SsoAuthService.class);
    private final SsoAuthUserDetailsServiceImpl service = new SsoAuthUserDetailsServiceImpl(ssoAuthService);

    @Test
    void loadUserById_delegatesToSsoAuthService() {
        User rebuilt = new User();
        rebuilt.setId(1001L);
        when(ssoAuthService.loadUserByUpstreamToken(1001L)).thenReturn(rebuilt);

        assertThat(service.loadUserById(1001L)).isSameAs(rebuilt);
        verify(ssoAuthService).loadUserByUpstreamToken(1001L);

        when(ssoAuthService.loadUserByUpstreamToken(9999L)).thenReturn(null);
        assertThat(service.loadUserById(9999L)).isNull();
    }

    @Test
    void loadUserByAccount_alwaysNull() {
        assertThat(service.loadUserByAccount("alice")).isNull();
    }
}
