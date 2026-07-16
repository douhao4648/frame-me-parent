package com.frame.me.auth.rbac.permission;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataPermissionResolver} 单元测试.
 *
 * @author frame-me
 */
class DataPermissionResolverTest {

    private DataPermission dp(String resource, String action, String scope, Long... ids) {
        return new DataPermission(resource, action, scope, Set.of(ids));
    }

    @Test
    void matching_filtersByResourceAndAction() {
        List<DataPermission> dps = List.of(
                dp("order", "read", "SELF"),
                dp("order", "write", "DEPT"),
                dp("dept", "read", "ALL"));

        assertEquals(2, DataPermissionResolver.matching(dps, "order", null).size(), "action 为 null 时只按资源匹配");
        assertEquals(1, DataPermissionResolver.matching(dps, "order", "read").size());
        assertEquals(0, DataPermissionResolver.matching(dps, "order", "delete").size());
    }

    @Test
    void matching_wildcardAndCaseInsensitive() {
        List<DataPermission> dps = List.of(dp("order", "*", "SELF"));

        assertEquals(1, DataPermissionResolver.matching(dps, "ORDER", "read").size(),
                "授权侧 action 为 * 应匹配任意操作，比较忽略大小写");
        assertEquals(1, DataPermissionResolver.matching(dps, "order", null).size());
    }

    @Test
    void matching_nullOrEmptyInput_returnsEmpty() {
        assertTrue(DataPermissionResolver.matching(null, "order", null).isEmpty());
        assertTrue(DataPermissionResolver.matching(List.of(), "order", null).isEmpty());
        assertTrue(DataPermissionResolver.matching(List.of(dp("order", "*", "SELF")), null, null).isEmpty());
    }

    @Test
    void isAll_anyAllScopeWins() {
        List<DataPermission> dps = List.of(
                dp("order", "*", "SELF"),
                dp("order", "*", "all"));

        assertTrue(DataPermissionResolver.isAll(dps, "order", null), "scope 比较忽略大小写");
        assertFalse(DataPermissionResolver.isAll(dps, "dept", null));
    }

    @Test
    void scopesAndDataIds_unionAcrossEntries() {
        List<DataPermission> dps = List.of(
                dp("order", "*", "SELF", 1L),
                dp("order", "*", "CUSTOM", 2L, 3L),
                dp("dept", "*", "ALL"));

        assertEquals(Set.of("SELF", "CUSTOM"), DataPermissionResolver.scopes(dps, "order", null));
        assertEquals(Set.of(1L, 2L, 3L), DataPermissionResolver.dataIds(dps, "order", null));
        assertTrue(DataPermissionResolver.dataIds(dps, "unknown", null).isEmpty());
    }

    @Test
    void check_allOrIdsOrNullId() {
        List<DataPermission> customDps = List.of(dp("order", "*", "CUSTOM", 5L));
        List<DataPermission> allDps = List.of(dp("order", "*", "ALL"));

        assertTrue(DataPermissionResolver.check(allDps, "order", null, 99L), "ALL 范围应短路放行");
        assertTrue(DataPermissionResolver.check(allDps, "order", null, null), "ALL 范围即使 id 为空也放行");
        assertTrue(DataPermissionResolver.check(customDps, "order", null, 5L));
        assertFalse(DataPermissionResolver.check(customDps, "order", null, 6L));
        assertFalse(DataPermissionResolver.check(customDps, "order", null, null), "id 为空时仅 ALL 可通过");
    }

    @Test
    void toLong_conversions() {
        assertEquals(5L, DataPermissionResolver.toLong(5), "Integer 应强转");
        assertEquals(5L, DataPermissionResolver.toLong(5L));
        assertEquals(5L, DataPermissionResolver.toLong("5"), "数字字符串应解析");
        assertEquals(5L, DataPermissionResolver.toLong(" 5 "), "数字字符串应先去空白");
        assertNull(DataPermissionResolver.toLong("abc"), "非法字符串返回 null");
        assertNull(DataPermissionResolver.toLong(null));
        assertNull(DataPermissionResolver.toLong(new Object()), "不支持的类型返回 null");
    }
}
