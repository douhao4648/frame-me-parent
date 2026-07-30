package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.SaManager;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.base.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * {@link SaTokenAuthUserResolver} 单元测试：显式 header 读取 + 委托 {@link IAuthService#getUser}.
 *
 * <p>token 头名取原生配置单一事实源 {@code SaManager.getConfig().getTokenName()}
 * （默认 {@code satoken}）。</p>
 *
 * @author frame-me
 */
class SaTokenAuthUserResolverTest {

    private final IAuthService authService = mock(IAuthService.class);
    private final SaTokenAuthUserResolver resolver = new SaTokenAuthUserResolver(authService);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    private String tokenName() {
        return SaManager.getConfig().getTokenName();
    }

    /**
     * 请求头携带 token → 解析出用户.
     */
    @Test
    void resolve_withTokenHeader_returnsUser() {
        User user = new User();
        user.setId(1L);
        user.setAccount("alice");
        when(request.getHeader(tokenName())).thenReturn("token-abc");
        when(authService.getUser("token-abc")).thenReturn(user);

        User resolved = resolver.resolve(request);

        assertThat(resolved).isNotNull();
        assertThat(resolved.getAccount()).isEqualTo("alice");
    }

    /**
     * 请求头缺失 / 空白 → 返回 null，且不触碰认证服务.
     */
    @Test
    void resolve_missingOrBlankHeader_returnsNull() {
        when(request.getHeader(tokenName())).thenReturn(null);
        assertThat(resolver.resolve(request)).isNull();

        when(request.getHeader(tokenName())).thenReturn("   ");
        assertThat(resolver.resolve(request)).isNull();

        verify(authService, never()).getUser(anyString());
    }

    /**
     * token 无效（认证服务返回 null）→ 返回 null.
     */
    @Test
    void resolve_invalidToken_returnsNull() {
        when(request.getHeader(tokenName())).thenReturn("bad-token");
        when(authService.getUser("bad-token")).thenReturn(null);

        assertThat(resolver.resolve(request)).isNull();
    }

    /**
     * 自定义 token 头名生效（原生 {@code sa-token.token-name} 配置）.
     */
    @Test
    void resolve_customTokenNameHeader() {
        String original = tokenName();
        SaManager.getConfig().setTokenName("x-auth-token");
        try {
            User user = new User();
            user.setId(2L);
            when(request.getHeader("x-auth-token")).thenReturn("token-xyz");
            when(authService.getUser("token-xyz")).thenReturn(user);

            assertThat(resolver.resolve(request)).isSameAs(user);
        } finally {
            SaManager.getConfig().setTokenName(original);
        }
    }

    /**
     * 无 header 有同名 Cookie → Cookie 兜底解析成功.
     */
    @Test
    void resolve_cookieFallbackWhenNoHeader_returnsUser() {
        User user = new User();
        user.setId(3L);
        user.setAccount("bob");
        when(request.getHeader(tokenName())).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("satoken", "cookie-token")});
        when(authService.getUser("cookie-token")).thenReturn(user);

        assertThat(resolver.resolve(request)).isSameAs(user);
    }

    /**
     * header 优先于 Cookie：两者同时存在时取 header 值.
     */
    @Test
    void resolve_headerTakesPrecedenceOverCookie() {
        User user = new User();
        user.setId(4L);
        when(request.getHeader(tokenName())).thenReturn("header-token");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("satoken", "cookie-token")});
        when(authService.getUser("header-token")).thenReturn(user);

        assertThat(resolver.resolve(request)).isSameAs(user);

        verify(authService).getUser("header-token");
        verify(authService, never()).getUser("cookie-token");
    }

    /**
     * header 空白时回退 Cookie；Cookie 名不匹配或值空白时返回 null.
     */
    @Test
    void resolve_blankHeaderFallsBackToCookie() {
        User user = new User();
        when(request.getHeader(tokenName())).thenReturn("   ");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("satoken", " cookie-token ")});
        when(authService.getUser("cookie-token")).thenReturn(user);

        assertThat(resolver.resolve(request)).isSameAs(user);
    }

    /**
     * Cookie 名不匹配 / Cookie 值空白 → 返回 null，且不触碰认证服务.
     */
    @Test
    void resolve_cookieNameMismatchOrBlankValue_returnsNull() {
        when(request.getHeader(tokenName())).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "cookie-token")});
        assertThat(resolver.resolve(request)).isNull();

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("satoken", "  ")});
        assertThat(resolver.resolve(request)).isNull();

        verify(authService, never()).getUser(anyString());
    }

    /**
     * 原生 {@code sa-token.is-read-cookie=false} 时 Cookie 兜底关闭：
     * 无 header 只有 Cookie 也返回 null，与原生读取行为对齐（开关不被架空）.
     */
    @Test
    void resolve_cookieFallbackDisabledWhenIsReadCookieFalse() {
        boolean original = SaManager.getConfig().getIsReadCookie();
        SaManager.getConfig().setIsReadCookie(false);
        try {
            when(request.getHeader(tokenName())).thenReturn(null);
            when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("satoken", "cookie-token")});

            assertThat(resolver.resolve(request)).isNull();
            assertThat(SaTokenAuthUserResolver.extractToken(request)).isNull();
            verify(authService, never()).getUser(anyString());
        } finally {
            SaManager.getConfig().setIsReadCookie(original);
        }
    }

    /**
     * extractToken（Controller logout/refresh 入口）：无 header 时同样 Cookie 兜底，
     * Cookie-only 客户端不再拿不到 token.
     */
    @Test
    void extractToken_cookieFallbackWhenNoHeader() {
        when(request.getHeader(tokenName())).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("satoken", "cookie-token")});

        assertThat(SaTokenAuthUserResolver.extractToken(request)).isEqualTo("cookie-token");
    }

    /**
     * extractToken：header 优先于 Cookie；两者都缺失时返回 null.
     */
    @Test
    void extractToken_headerTakesPrecedenceAndNullWhenAbsent() {
        when(request.getHeader(tokenName())).thenReturn("header-token");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("satoken", "cookie-token")});
        assertThat(SaTokenAuthUserResolver.extractToken(request)).isEqualTo("header-token");

        when(request.getHeader(tokenName())).thenReturn(null);
        when(request.getCookies()).thenReturn(null);
        assertThat(SaTokenAuthUserResolver.extractToken(request)).isNull();
    }
}
