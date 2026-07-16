package com.frame.me.auth.rbac.permission;

import com.frame.me.auth.core.AuthContext;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthDataPermissions} 静态入口与 {@link AuthExpressionRoot} 数据权限 SpEL 函数测试.
 *
 * @author frame-me
 */
class AuthDataPermissionsTest {

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        AuthPermissionHolder.clear();
    }

    private void loadHolder() {
        AuthPermissionHolder.setDataPermissions(List.of(
                new DataPermission("order", "*", "CUSTOM", Set.of(5L, 7L)),
                new DataPermission("dept", "*", "ALL", Set.of())));
    }

    @Test
    void helper_readsFromHolder() {
        loadHolder();

        assertTrue(AuthDataPermissions.isAll("dept"));
        assertFalse(AuthDataPermissions.isAll("order"));
        assertEquals(Set.of("CUSTOM"), AuthDataPermissions.scopes("order"));
        assertEquals(Set.of(5L, 7L), AuthDataPermissions.dataIds("order"));
        assertTrue(AuthDataPermissions.check("order", 5));
        assertTrue(AuthDataPermissions.check("order", "7"), "数字字符串 id 应可解析");
        assertFalse(AuthDataPermissions.check("order", 9));
        assertTrue(AuthDataPermissions.check("dept", 999), "ALL 范围应短路放行");
    }

    @Test
    void helper_emptyHolder_returnsEmpty() {
        assertFalse(AuthDataPermissions.isAll("order"));
        assertTrue(AuthDataPermissions.scopes("order").isEmpty());
        assertTrue(AuthDataPermissions.dataIds("order").isEmpty());
        assertFalse(AuthDataPermissions.check("order", 1L));
    }

    @Test
    void spel_isAllAndCheck() {
        loadHolder();

        assertTrue(AuthExpressionRoot.evaluate("dataCheck('order', 5)"), "Integer 字面量 id 应可判定");
        assertTrue(AuthExpressionRoot.evaluate("dataCheck('order', '7')"), "数字字符串 id 应可判定");
        assertFalse(AuthExpressionRoot.evaluate("dataCheck('order', 9)"));
        assertFalse(AuthExpressionRoot.evaluate("dataCheck('unknown', 1)"));
        assertTrue(AuthExpressionRoot.evaluate("dataCheck('dept', 999)"), "ALL 范围应短路放行");
        assertTrue(AuthExpressionRoot.evaluate("dataIsAll('dept')"));
        assertFalse(AuthExpressionRoot.evaluate("dataIsAll('order')"));
    }

    @Test
    void spel_actionVariants() {
        AuthPermissionHolder.setDataPermissions(List.of(
                new DataPermission("order", "read", "CUSTOM", Set.of(5L))));

        assertTrue(AuthExpressionRoot.evaluate("dataCheck('order', 'read', 5)"));
        assertFalse(AuthExpressionRoot.evaluate("dataCheck('order', 'write', 5)"), "action 不匹配应拒绝");
        assertFalse(AuthExpressionRoot.evaluate("dataIsAll('order', 'read')"), "CUSTOM 范围不是 ALL");
    }

    @Test
    void spel_emptyHolder_allFalse() {
        assertFalse(AuthExpressionRoot.evaluate("dataCheck('order', 5)"));
        assertFalse(AuthExpressionRoot.evaluate("dataIsAll('order')"));
    }

    @Test
    void spel_withVariables_uriTemplateStyle() {
        loadHolder();

        assertTrue(AuthExpressionRoot.evaluate("dataCheck('order', #id)", Map.of("id", "5")),
                "URI 模板变量为字符串，dataId 应可解析");
        assertTrue(AuthExpressionRoot.evaluate("dataCheck('order', #id)", Map.of("id", 7)));
        assertFalse(AuthExpressionRoot.evaluate("dataCheck('order', #id)", Map.of("id", 9)));
        assertFalse(AuthExpressionRoot.evaluate("dataCheck('order', #missing)", Map.of("id", 5)),
                "变量不存在时求值异常应返回 false");
    }

    @Test
    void helper_checkOwner() {
        AuthContext.setUser(createUser(7L));
        AuthPermissionHolder.setDataPermissions(List.of(
                new DataPermission("order", "*", "SELF", Set.of()),
                new DataPermission("dept", "*", "ALL", Set.of())));

        assertTrue(AuthDataPermissions.checkOwner("dept", 999L), "ALL 范围应短路放行");
        assertTrue(AuthDataPermissions.checkOwner("order", 7L), "SELF 且 owner 为当前用户应放行");
        assertFalse(AuthDataPermissions.checkOwner("order", 8L), "SELF 但 owner 非当前用户应拒绝");
        assertFalse(AuthDataPermissions.checkOwner("order", null), "ownerId 为 null 应拒绝");
        assertFalse(AuthDataPermissions.checkOwner("unknown", 7L), "无该资源数据权限应拒绝");
    }

    @Test
    void helper_checkOwner_actionVariant() {
        AuthContext.setUser(createUser(7L));
        AuthPermissionHolder.setDataPermissions(List.of(
                new DataPermission("order", "write", "SELF", Set.of())));

        assertTrue(AuthDataPermissions.checkOwner("order", "write", 7L));
        assertFalse(AuthDataPermissions.checkOwner("order", "read", 7L), "action 不匹配应拒绝");
    }

    @Test
    void helper_checkOwner_emptyHolder() {
        AuthContext.setUser(createUser(7L));

        assertFalse(AuthDataPermissions.checkOwner("order", 7L));
    }

    @Test
    void helper_checkDept() {
        User user = createUser(7L);
        user.setDeptId(100L);
        AuthContext.setUser(user);
        AuthPermissionHolder.setDataPermissions(List.of(
                new DataPermission("order", "*", "DEPT", Set.of()),
                new DataPermission("dept", "*", "ALL", Set.of()),
                new DataPermission("region", "read", "DEPT", Set.of())));

        assertTrue(AuthDataPermissions.checkDept("dept", 999L), "ALL 范围应短路放行");
        assertTrue(AuthDataPermissions.checkDept("order", 100L), "DEPT 且行部门等于用户部门应放行");
        assertFalse(AuthDataPermissions.checkDept("order", 200L), "DEPT 但行部门非用户部门应拒绝");
        assertFalse(AuthDataPermissions.checkDept("order", null), "deptId 为 null 应拒绝");
        assertFalse(AuthDataPermissions.checkDept("unknown", 100L), "无该资源数据权限应拒绝");
        assertTrue(AuthDataPermissions.checkDept("region", "read", 100L), "action 变体命中应放行");
        assertFalse(AuthDataPermissions.checkDept("region", "write", 100L), "action 不匹配应拒绝");
    }

    @Test
    void helper_checkDept_userDeptNull_rejects() {
        // 用户无部门信息:fail-closed 拒绝
        AuthContext.setUser(createUser(7L));
        AuthPermissionHolder.setDataPermissions(List.of(
                new DataPermission("order", "*", "DEPT", Set.of())));

        assertFalse(AuthDataPermissions.checkDept("order", 100L));
    }

    @Test
    void helper_checkOrg() {
        User user = createUser(7L);
        user.setOrgId(10L);
        AuthContext.setUser(user);
        AuthPermissionHolder.setDataPermissions(List.of(
                new DataPermission("order", "*", "ORG", Set.of())));

        assertTrue(AuthDataPermissions.checkOrg("order", 10L), "ORG 且行机构等于用户机构应放行");
        assertFalse(AuthDataPermissions.checkOrg("order", 20L), "ORG 但行机构非用户机构应拒绝");
        assertFalse(AuthDataPermissions.checkOrg("order", null), "orgId 为 null 应拒绝");
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
