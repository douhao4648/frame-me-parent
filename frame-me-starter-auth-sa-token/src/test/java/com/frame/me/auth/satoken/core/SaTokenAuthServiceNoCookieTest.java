package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaResponseForMock;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import com.frame.me.auth.spi.IAuthUserDetailsService;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * sa-token 原生 Cookie 关闭行为测试（{@code sa-token.is-read-cookie=false}）.
 *
 * <p>关闭原生 Cookie 读写后，登录 / 登出 / 续期均不产生 Set-Cookie 头，
 * Token 仅经 JSON body 返回（header 通道）。注意 sa-token 原生默认
 * {@code is-read-cookie=true}，即默认即写 Cookie——本类验证的是显式关闭后的行为。</p>
 *
 * @author frame-me
 */
class SaTokenAuthServiceNoCookieTest {

    private static final String RAW_PASSWORD = "123456";

    private final IAuthUserDetailsService userDetailsService = mock(IAuthUserDetailsService.class);
    private final SaTokenAuthService authService = new SaTokenAuthService(userDetailsService);

    @BeforeEach
    void setUp() {
        SaTokenConfig config = new SaTokenConfig();
        config.setIsReadCookie(false);
        SaManager.setConfig(config);
        SaTokenContextMockUtil.setMockContext();

        User user = new User();
        user.setId(92002L);
        user.setAccount("bob");
        when(userDetailsService.loadUserByAccount("bob")).thenReturn(user);
        when(userDetailsService.matches(eq(RAW_PASSWORD), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
        // 还原全局默认配置，避免静态状态外溢到其他测试类
        SaManager.setConfig(new SaTokenConfig());
    }

    private boolean hasSetCookie() {
        return ((SaResponseForMock) SaHolder.getResponse()).headerMap.containsKey("Set-Cookie");
    }

    /**
     * 登录：is-read-cookie=false 时不写 Set-Cookie.
     */
    @Test
    void login_noCookieWritten() {
        authService.login("bob", RAW_PASSWORD);
        assertThat(hasSetCookie()).isFalse();
    }

    /**
     * 登出：is-read-cookie=false 时不清 Cookie（本无 Cookie 可清）.
     */
    @Test
    void logout_noCookieCleared() {
        String token = authService.login("bob", RAW_PASSWORD);
        authService.logout(token);
        assertThat(hasSetCookie()).isFalse();
    }

    /**
     * 续期：is-read-cookie=false 时不刷 Cookie.
     */
    @Test
    void refresh_noCookieRenewed() {
        String token = authService.login("bob", RAW_PASSWORD);
        authService.refresh(token);
        assertThat(hasSetCookie()).isFalse();
    }
}
