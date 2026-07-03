package com.frame.me.auth.propagation;

import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.core.AuthContext;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link AuthContextTaskDecorator} 单元测试.
 *
 * @author frame-me
 */
class AuthContextTaskDecoratorTest {

    private AuthProperties properties;
    private AuthContextTaskDecorator decorator;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        decorator = new AuthContextTaskDecorator(properties);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        AuthContext.clear();
        AuthPropagationHolder.clear();
    }

    @Test
    void testPropagateWhenAsyncEnabled() {
        properties.getPropagate().getAsync().setEnabled(true);
        AuthContext.setUser(createUser(1L, "admin"));
        bindRequestWithHeader("Authorization", "Bearer token123");

        AtomicReference<User> capturedUser = new AtomicReference<>();
        AtomicReference<String> capturedHeader = new AtomicReference<>();
        Runnable task = decorator.decorate(() -> {
            capturedUser.set(AuthContext.getUser());
            capturedHeader.set(AuthPropagationHolder.getHeader("Authorization"));
        });

        // 模拟切换到异步线程
        AuthContext.clear();
        AuthPropagationHolder.clear();
        task.run();

        assertEquals(Long.valueOf(1L), capturedUser.get().getId());
        assertEquals("Bearer token123", capturedHeader.get());
    }

    @Test
    void testDoNotPropagateWhenAsyncDisabled() {
        properties.getPropagate().getAsync().setEnabled(false);
        AuthContext.setUser(createUser(1L, "admin"));

        AtomicReference<User> captured = new AtomicReference<>();
        Runnable task = decorator.decorate(() -> captured.set(AuthContext.getUser()));

        AuthContext.clear();
        task.run();

        assertNull(captured.get());
    }

    @Test
    void testPropagateOnlyUserWhenNoRequest() {
        AuthContext.setUser(createUser(2L, "user"));

        AtomicReference<User> captured = new AtomicReference<>();
        Runnable task = decorator.decorate(() -> captured.set(AuthContext.getUser()));

        AuthContext.clear();
        task.run();

        assertEquals(Long.valueOf(2L), captured.get().getId());
    }

    private void bindRequestWithHeader(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(name, value);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private User createUser(Long id, String account) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        return user;
    }
}
