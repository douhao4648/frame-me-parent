package com.frame.me.auth.core;

import com.frame.me.base.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link HeaderAuthUserResolver} 单元测试.
 *
 * @author frame-me
 */
class HeaderAuthUserResolverTest {

    private final HeaderAuthUserResolver resolver = new HeaderAuthUserResolver();

    @Test
    void testResolveWithValidHeader() {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader(HeaderAuthUserResolver.HEADER_USER_ID, "1001");
        ((MockHttpServletRequest) request).addHeader(HeaderAuthUserResolver.HEADER_USER_ACCOUNT, "admin");

        User user = resolver.resolve(request);

        assertEquals(1001L, user.getId());
        assertEquals("admin", user.getAccount());
    }

    @Test
    void testResolveWithoutUserId() {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader(HeaderAuthUserResolver.HEADER_USER_ACCOUNT, "admin");

        assertNull(resolver.resolve(request));
    }

    @Test
    void testResolveWithInvalidUserId() {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader(HeaderAuthUserResolver.HEADER_USER_ID, "not-a-number");

        assertNull(resolver.resolve(request));
    }
}
