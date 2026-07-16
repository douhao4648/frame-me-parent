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
}
