package com.frame.me.auth.rbac.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataPermission} 单元测试.
 *
 * @author frame-me
 */
class DataPermissionTest {

    @Test
    void testExactMatch() {
        DataPermission permission = new DataPermission("user", "read", "SELF", null);
        assertTrue(permission.matches("user", "read"));
        assertFalse(permission.matches("user", "write"));
        assertFalse(permission.matches("order", "read"));
    }

    @Test
    void testWildcardResource() {
        DataPermission permission = new DataPermission("*", "read", "ALL", null);
        assertTrue(permission.matches("user", "read"));
        assertTrue(permission.matches("order", "read"));
        assertFalse(permission.matches("user", "write"));
    }

    @Test
    void testWildcardAction() {
        DataPermission permission = new DataPermission("user", "*", "ALL", null);
        assertTrue(permission.matches("user", "read"));
        assertTrue(permission.matches("user", "write"));
        assertFalse(permission.matches("order", "read"));
    }

    @Test
    void testIgnoreCase() {
        DataPermission permission = new DataPermission("User", "Read", "SELF", null);
        assertTrue(permission.matches("user", "read"));
    }

    /**
     * 授权侧字段为 null（如反序列化缺字段的半成品对象）判不匹配：fail-closed，不抛 NPE.
     *
     * <p>对照 {@link PermissionTest#testNullGrantSideFailsClosed}：旧实现 {@code matchesValue}
     * 缺 {@code pattern != null} 防御，{@code resource} 为 null 时调 {@code pattern.equalsIgnoreCase}
     * 抛 NPE。修复后应与 {@link Permission} 一致 fail-closed 返回 false.</p>
     */
    @Test
    void testNullGrantSideFailsClosed() {
        DataPermission empty = new DataPermission();
        assertFalse(empty.matches("user", "read"));
        assertFalse(empty.matchesResource("user"));

        DataPermission nullAction = new DataPermission("user", null, "SELF", null);
        assertFalse(nullAction.matches("user", "read"));
        assertTrue(nullAction.matchesResource("user"), "resource 非 null 时资源匹配不受影响");

        DataPermission nullResource = new DataPermission(null, "read", "SELF", null);
        assertFalse(nullResource.matches("user", "read"));
        assertFalse(nullResource.matchesResource("user"));
    }
}
