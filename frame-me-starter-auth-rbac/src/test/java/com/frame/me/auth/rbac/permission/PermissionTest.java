package com.frame.me.auth.rbac.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Permission} 单元测试.
 *
 * @author frame-me
 */
class PermissionTest {

    @Test
    void testExactMatch() {
        Permission permission = new Permission("user", "read");
        assertTrue(permission.matches("user", "read"));
        assertFalse(permission.matches("user", "write"));
        assertFalse(permission.matches("order", "read"));
    }

    @Test
    void testWildcardResource() {
        Permission permission = new Permission("*", "read");
        assertTrue(permission.matches("user", "read"));
        assertTrue(permission.matches("order", "read"));
        assertFalse(permission.matches("user", "write"));
    }

    @Test
    void testWildcardAction() {
        Permission permission = new Permission("user", "*");
        assertTrue(permission.matches("user", "read"));
        assertTrue(permission.matches("user", "write"));
        assertFalse(permission.matches("order", "read"));
    }

    @Test
    void testIgnoreCase() {
        Permission permission = new Permission("User", "Read");
        assertTrue(permission.matches("user", "read"));
    }

    /**
     * 授权侧字段为 null（如反序列化缺字段的半成品对象）判不匹配：fail-closed，不抛 NPE.
     */
    @Test
    void testNullGrantSideFailsClosed() {
        Permission empty = new Permission();
        assertFalse(empty.matches("user", "read"));
        assertFalse(empty.matchesResource("user"));

        Permission nullAction = new Permission("user", null);
        assertFalse(nullAction.matches("user", "read"));
        assertTrue(nullAction.matchesResource("user"), "resource 非 null 时资源匹配不受影响");
    }
}
