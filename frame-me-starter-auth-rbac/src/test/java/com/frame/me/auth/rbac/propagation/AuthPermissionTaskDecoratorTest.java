package com.frame.me.auth.rbac.propagation;

import com.frame.me.auth.rbac.permission.AuthPermissionHolder;
import com.frame.me.auth.rbac.permission.DataPermission;
import com.frame.me.auth.rbac.permission.Permission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthPermissionTaskDecorator} 单元测试.
 *
 * @author frame-me
 */
class AuthPermissionTaskDecoratorTest {

    private final AuthPermissionTaskDecorator decorator = new AuthPermissionTaskDecorator();

    @AfterEach
    void tearDown() {
        AuthPermissionHolder.clear();
    }

    @Test
    void propagatesLoadedPermissionsAndClearsAfter() {
        // 主线程加载权限上下文
        AuthPermissionHolder.setRoles(Set.of("admin"));
        AuthPermissionHolder.setPermissions(List.of(new Permission("order", "r")));
        AuthPermissionHolder.markLoaded();

        AtomicReference<Set<String>> seenRoles = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seenRoles.set(AuthPermissionHolder.getRoles()));

        // 模拟请求线程结束（上下文被清理）后，在异步线程执行
        AuthPermissionHolder.clear();
        decorated.run();

        assertEquals(Set.of("admin"), seenRoles.get(), "异步线程应恢复到传播的角色");
        assertFalse(AuthPermissionHolder.isLoaded(), "执行后应清理上下文");
    }

    @Test
    void noContextReturnsOriginalRunnable() {
        Runnable original = () -> {
        };
        assertSame(original, decorator.decorate(original), "未加载权限时应原样返回，不做包装");
    }

    @Test
    void propagatesDataPermissions() {
        AuthPermissionHolder.setDataPermissions(
                java.util.List.of(new DataPermission("order", "*", "SELF", java.util.Set.of(5L))));
        AuthPermissionHolder.markLoaded();

        AtomicReference<Set<DataPermission>> seen = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seen.set(AuthPermissionHolder.getDataPermissions()));

        AuthPermissionHolder.clear();
        decorated.run();

        assertEquals(1, seen.get().size(), "异步线程应恢复到传播的数据权限");
        assertFalse(AuthPermissionHolder.isLoaded(), "执行后应清理上下文");
    }

    /**
     * 线程池饱和（CALLER_RUNS）：装饰后的任务在提交（请求）线程同步执行，
     * 执行后必须恢复提交线程原有权限上下文，而不是 finally 直接清空.
     */
    @Test
    void callerRuns_restoresSubmittingThreadContext() {
        AuthPermissionHolder.setRoles(Set.of("admin"));
        AuthPermissionHolder.setPermissions(List.of(new Permission("order", "r")));
        AuthPermissionHolder.markLoaded();

        AtomicReference<Set<String>> seenRoles = new AtomicReference<>();
        decorator.decorate(() -> seenRoles.set(AuthPermissionHolder.getRoles())).run();

        assertEquals(Set.of("admin"), seenRoles.get());
        assertTrue(AuthPermissionHolder.isLoaded(), "CALLER_RUNS 执行后请求线程权限上下文不得被清空");
        assertEquals(Set.of("admin"), AuthPermissionHolder.getRoles());
    }

    /**
     * 嵌套提交：外层异步任务线程内同步执行内层装饰任务（内层 CALLER_RUNS），
     * 内层 finally 不得清掉外层任务的上下文；外层结束（线程原本无上下文）仍应清理干净.
     */
    @Test
    void nestedSubmission_innerClearDoesNotWipeOuterContext() {
        AuthPermissionHolder.setRoles(Set.of("admin"));
        AuthPermissionHolder.markLoaded();
        Runnable inner = decorator.decorate(() ->
                assertEquals(Set.of("admin"), AuthPermissionHolder.getRoles(), "内层应看到传播的角色"));
        AtomicReference<Boolean> outerSeenAfterInner = new AtomicReference<>();
        Runnable outer = decorator.decorate(() -> {
            inner.run();
            outerSeenAfterInner.set(AuthPermissionHolder.isLoaded());
        });

        // 模拟外层任务在无线程上下文的异步线程执行
        AuthPermissionHolder.clear();
        outer.run();

        assertEquals(Boolean.TRUE, outerSeenAfterInner.get(), "内层 finally 不得清掉外层任务的权限上下文");
        assertFalse(AuthPermissionHolder.isLoaded(), "外层结束后（线程原本无上下文）应清理干净");
    }
}
