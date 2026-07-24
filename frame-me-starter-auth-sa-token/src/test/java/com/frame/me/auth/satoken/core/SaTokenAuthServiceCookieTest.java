package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaCookieConfig;
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
 * sa-token 原生 Cookie 行为测试（配置 {@code sa-token.cookie.*} + 默认 {@code is-read-cookie=true}）.
 *
 * <p>用 sa-token 官方 {@link SaTokenContextMockUtil} 搭建请求上下文，以真实
 * {@link SaTokenAuthService} 走标准 {@code StpUtil.login / logout / renewTimeout} 链路，
 * 断言原生写入 {@link SaResponseForMock} 的 Set-Cookie 头——Cookie 属性全部来自
 * {@code SaManager} 原生配置（等价于 Spring 环境的 {@code sa-token.cookie.*} 绑定），
 * 框架自身不写任何 Cookie。</p>
 *
 * @author frame-me
 */
class SaTokenAuthServiceCookieTest {

    private static final long TIMEOUT_SECONDS = 3600L;
    private static final String RAW_PASSWORD = "123456";

    private final IAuthUserDetailsService userDetailsService = mock(IAuthUserDetailsService.class);
    private final SaTokenAuthService authService = new SaTokenAuthService(userDetailsService);

    @BeforeEach
    void setUp() {
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("satoken");
        config.setTimeout(TIMEOUT_SECONDS);
        config.setCookie(new SaCookieConfig()
                .setDomain(".example.com")
                .setPath("/")
                .setSecure(true)
                .setHttpOnly(true)
                .setSameSite("Lax"));
        SaManager.setConfig(config);
        SaTokenContextMockUtil.setMockContext();

        User user = new User();
        user.setId(92001L);
        user.setAccount("alice");
        when(userDetailsService.loadUserByAccount("alice")).thenReturn(user);
        when(userDetailsService.matches(eq(RAW_PASSWORD), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
        // 还原全局默认配置，避免静态状态外溢到其他测试类
        SaManager.setConfig(new SaTokenConfig());
    }

    private String setCookieHeader() {
        return ((SaResponseForMock) SaHolder.getResponse()).headerMap.get("Set-Cookie");
    }

    /**
     * 登录：原生写 Cookie——名取 tokenName，Domain/Secure/HttpOnly/SameSite 来自
     * cookie 配置，Max-Age 由 is-lasting-cookie（默认 true）+ timeout 派生.
     */
    @Test
    void login_writesNativeCookie() {
        String token = authService.login("alice", RAW_PASSWORD);

        String setCookie = setCookieHeader();
        assertThat(setCookie).contains("satoken=" + token);
        assertThat(setCookie).contains("Max-Age=" + TIMEOUT_SECONDS);
        assertThat(setCookie).contains("Domain=.example.com");
        assertThat(setCookie).contains("Path=/");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
    }

    /**
     * 登出：原生清 Cookie——同名空值 + Max-Age=0（以增代删），Domain/Path 与写入时一致.
     */
    @Test
    void logout_clearsNativeCookie() {
        String token = authService.login("alice", RAW_PASSWORD);

        authService.logout(token);

        String setCookie = setCookieHeader();
        assertThat(setCookie).startsWith("satoken=");
        assertThat(setCookie).doesNotContain("satoken=" + token);
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("Domain=.example.com");
    }

    /**
     * 续期：原生刷新 Cookie——重写同名 Cookie，Max-Age 取原生 timeout 秒数.
     */
    @Test
    void refresh_renewsNativeCookie() {
        String token = authService.login("alice", RAW_PASSWORD);

        authService.refresh(token);

        String setCookie = setCookieHeader();
        assertThat(setCookie).contains("satoken=" + token);
        assertThat(setCookie).contains("Max-Age=" + TIMEOUT_SECONDS);
        assertThat(setCookie).contains("Domain=.example.com");
    }
}
