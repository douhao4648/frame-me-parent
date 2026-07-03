package com.frame.me.auth.resolver;

import com.frame.me.auth.annotation.LoginUser;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LoginUserArgumentResolver} 单元测试.
 *
 * @author frame-me
 */
class LoginUserArgumentResolverTest {

    private final LoginUserArgumentResolver resolver = new LoginUserArgumentResolver();

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void testSupportsParameterWithAnnotation() throws NoSuchMethodException {
        MethodParameter parameter = MethodParameter.forExecutable(
                TestController.class.getMethod("me", User.class), 0);
        assertTrue(resolver.supportsParameter(parameter));
    }

    @Test
    void testSupportsParameterWithoutAnnotation() throws NoSuchMethodException {
        MethodParameter parameter = MethodParameter.forExecutable(
                TestController.class.getMethod("other", User.class), 0);
        assertFalse(resolver.supportsParameter(parameter));
    }

    @Test
    void testResolveArgumentReturnsCurrentUser() {
        User user = new User();
        user.setId(1L);
        user.setAccount("admin");
        AuthContext.setUser(user);

        NativeWebRequest request = new ServletWebRequest(new MockHttpServletRequest());
        Object resolved = resolver.resolveArgument(null, null, request, null);

        assertEquals(user, resolved);
    }

    @Test
    void testResolveArgumentReturnsNullWhenNotLogin() {
        NativeWebRequest request = new ServletWebRequest(new MockHttpServletRequest());
        Object resolved = resolver.resolveArgument(null, null, request, null);

        assertNull(resolved);
    }

    static class TestController {
        public String me(@LoginUser User user) {
            return "";
        }

        public String other(User user) {
            return "";
        }
    }
}
