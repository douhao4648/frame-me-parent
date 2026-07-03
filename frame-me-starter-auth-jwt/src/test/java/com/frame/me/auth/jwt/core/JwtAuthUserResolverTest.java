package com.frame.me.auth.jwt.core;

import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.base.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link JwtAuthUserResolver} 单元测试.
 *
 * @author frame-me
 */
class JwtAuthUserResolverTest {

    private final JwtAuthProperties properties = new JwtAuthProperties();
    private final IAuthService authService = mock(IAuthService.class);
    private final JwtAuthUserResolver resolver = new JwtAuthUserResolver(properties, authService);

    @Test
    void testResolveWithValidToken() {
        User user = new User();
        user.setId(1L);
        user.setAccount("admin");
        when(authService.getUser("token123")).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token123");

        assertEquals(user, resolver.resolve(request));
    }

    @Test
    void testResolveWithoutHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertNull(resolver.resolve(request));
        verifyNoInteractions(authService);
    }

    @Test
    void testResolveWithWrongPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic token123");

        assertNull(resolver.resolve(request));
        verifyNoInteractions(authService);
    }

    @Test
    void testResolveWithEmptyToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");
        when(authService.getUser("")).thenReturn(null);

        assertNull(resolver.resolve(request));
        verify(authService).getUser("");
    }
}
