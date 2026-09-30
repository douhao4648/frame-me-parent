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

    /**
     * 线程池饱和（CALLER_RUNS）：装饰后的任务在提交（请求）线程同步执行，
     * 执行后必须恢复请求线程原有身份与认证头，而不是 finally 直接清空.
     */
    @Test
    void callerRuns_restoresSubmittingThreadIdentity() {
        properties.getPropagate().getAsync().setEnabled(true);
        AuthContext.setUser(createUser(42L, "requester"));
        AuthPropagationHolder.setHeaders(java.util.Map.of("Authorization", "Bearer request-token"));

        AtomicReference<User> seenInTask = new AtomicReference<>();
        decorator.decorate(() -> seenInTask.set(AuthContext.getUser())).run();

        assertEquals(Long.valueOf(42L), seenInTask.get().getId(), "任务内应看到身份");
        User after = AuthContext.getUser();
        assertEquals(Long.valueOf(42L), after == null ? null : after.getId(),
                "CALLER_RUNS 执行后请求线程身份不得被清空");
        assertEquals("Bearer request-token", AuthPropagationHolder.getHeader("Authorization"),
                "CALLER_RUNS 执行后请求线程认证头不得被清空");
    }

    /**
     * 嵌套提交：外层异步任务线程内同步执行内层装饰任务（内层 CALLER_RUNS），
     * 内层 finally 不得清掉外层任务的上下文；外层结束（线程原本无上下文）仍应清理干净.
     */
    @Test
    void nestedSubmission_innerClearDoesNotWipeOuterContext() {
        properties.getPropagate().getAsync().setEnabled(true);
        AuthContext.setUser(createUser(42L, "requester"));
        Runnable inner = decorator.decorate(() ->
                assertEquals(Long.valueOf(42L), AuthContext.getUser().getId(), "内层应看到传播的身份"));
        AtomicReference<Long> outerSeenAfterInner = new AtomicReference<>();
        Runnable outer = decorator.decorate(() -> {
            inner.run();
            User user = AuthContext.getUser();
            outerSeenAfterInner.set(user == null ? null : user.getId());
        });

        // 模拟外层任务在无线程上下文的异步线程执行
        AuthContext.clear();
        outer.run();

        assertEquals(Long.valueOf(42L), outerSeenAfterInner.get(), "内层 finally 不得清掉外层任务的身份");
        assertNull(AuthContext.getUser(), "外层结束后（线程原本无上下文）应清理干净");
    }

    private void bindRequestWithHeader(String name, String value) {        MockHttpServletRequest request = new MockHttpServletRequest();
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
