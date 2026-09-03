package com.frame.me.auth.core;

import com.frame.me.base.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TrustedHeaderAuthUserResolver} 单元测试.
 *
 * @author frame-me
 */
class TrustedHeaderAuthUserResolverTest {

    private final TrustedHeaderAuthUserResolver resolver = new TrustedHeaderAuthUserResolver();

    @Test
    void testResolveWithValidHeader() {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader(TrustedHeaderAuthUserResolver.HEADER_USER_ID, "1001");
        ((MockHttpServletRequest) request).addHeader(TrustedHeaderAuthUserResolver.HEADER_USER_ACCOUNT, "admin");

        User user = resolver.resolve(request);

        assertEquals(1001L, user.getId());
        assertEquals("admin", user.getAccount());
    }

    @Test
    void testResolveWithoutUserId() {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader(TrustedHeaderAuthUserResolver.HEADER_USER_ACCOUNT, "admin");

        assertNull(resolver.resolve(request));
    }

    @Test
    void testResolveWithInvalidUserId() {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader(TrustedHeaderAuthUserResolver.HEADER_USER_ID, "not-a-number");

        assertNull(resolver.resolve(request));
    }

    /**
     * 回源模式：补全头里没有的字段（account 等），覆盖头传值.
     */
    @Test
    void testResolveFetchDetails_loadsFullUser() {
        TrustedHeaderAuthUserResolver fetching = new TrustedHeaderAuthUserResolver(detailsService(fullUser()));
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader(TrustedHeaderAuthUserResolver.HEADER_USER_ID, "1001");
        ((MockHttpServletRequest) request).addHeader(TrustedHeaderAuthUserResolver.HEADER_USER_ACCOUNT, "forged");

        User user = fetching.resolve(request);

        assertEquals(1001L, user.getId());
        assertEquals("admin", user.getAccount());
        assertEquals("管理员", user.getNickname());
    }

    /**
     * 回源模式：用户不存在（已删除）返回 null，fail-closed.
     */
    @Test
    void testResolveFetchDetails_userMissing_returnsNull() {
        TrustedHeaderAuthUserResolver fetching = new TrustedHeaderAuthUserResolver(detailsService(null));
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader(TrustedHeaderAuthUserResolver.HEADER_USER_ID, "1001");

        assertNull(fetching.resolve(request));
    }

    /**
     * 回源模式：禁用用户返回 null（修复纯头解析不校验状态的盲区）.
     */
    @Test
    void testResolveFetchDetails_userDisabled_returnsNull() {
        User disabled = fullUser();
        disabled.setStatus(0);
        TrustedHeaderAuthUserResolver fetching = new TrustedHeaderAuthUserResolver(detailsService(disabled));
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader(TrustedHeaderAuthUserResolver.HEADER_USER_ID, "1001");

        assertNull(fetching.resolve(request));
    }

    private static User fullUser() {
        User user = new User();
        user.setId(1001L);
        user.setAccount("admin");
        user.setNickname("管理员");
        user.setStatus(User.STATUS_ENABLED);
        return user;
    }

    private static com.frame.me.auth.spi.IAuthUserDetailsService detailsService(User user) {
        return new com.frame.me.auth.spi.IAuthUserDetailsService() {
            @Override
            public User loadUserByAccount(String account) {
                return null;
            }

            @Override
            public User loadUserById(Long id) {
                return user;
            }
        };
    }
}
