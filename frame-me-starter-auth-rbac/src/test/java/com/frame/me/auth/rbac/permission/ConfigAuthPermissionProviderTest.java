package com.frame.me.auth.rbac.permission;

import com.frame.me.auth.rbac.config.RbacProperties;
import com.frame.me.base.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConfigAuthPermissionProvider} 单元测试.
 *
 * @author frame-me
 */
class ConfigAuthPermissionProviderTest {

    private RbacProperties properties;
    private ConfigAuthPermissionProvider provider;

    @BeforeEach
    void setUp() {
        properties = new RbacProperties();
        provider = new ConfigAuthPermissionProvider(properties);
    }

    @Test
    void testGetRolesByUserId() {
        properties.getUsers().put("1", "admin,operator");

        User user = createUser(1L);
        Collection<String> roles = provider.getRoles(user);

        assertEquals(2, roles.size());
        assertTrue(roles.contains("admin"));
        assertTrue(roles.contains("operator"));
    }

    @Test
    void testGetPermissionsByRoles() {
        properties.getUsers().put("1", "admin");
        properties.getRoles().put("admin", "user:*,order:r");
        provider.init();

        User user = createUser(1L);
        Collection<Permission> permissions = provider.getPermissions(user);

        assertEquals(2, permissions.size());
        assertTrue(permissions.stream().anyMatch(p -> p.matches("user", "r")));
        assertTrue(permissions.stream().anyMatch(p -> p.matches("order", "r")));
    }

    @Test
    void testMergePermissionsFromMultipleRoles() {
        properties.getUsers().put("1", "admin,operator");
        properties.getRoles().put("admin", "user:*");
        properties.getRoles().put("operator", "order:r");
        provider.init();

        User user = createUser(1L);
        Collection<Permission> permissions = provider.getPermissions(user);

        assertEquals(2, permissions.size());
    }

    @Test
    void testActionDefaultsToStar() {
        properties.getUsers().put("1", "admin");
        properties.getRoles().put("admin", "order");
        provider.init();

        User user = createUser(1L);
        Collection<Permission> permissions = provider.getPermissions(user);

        assertEquals(1, permissions.size());
        Permission p = permissions.iterator().next();
        assertEquals("order", p.getResource());
        assertEquals("*", p.getAction());
    }

    @Test
    void testIgnoreEmptySegments() {
        properties.getUsers().put("1", "admin");
        properties.getRoles().put("admin", "user:r, ,,order:w");
        provider.init();

        User user = createUser(1L);
        Collection<Permission> permissions = provider.getPermissions(user);

        assertEquals(2, permissions.size());
    }

    /**
     * roles 配置段 resource 为空时启动期 fail-fast（与 data-scopes 同标准），
     * 避免配置笔误被静默吞成永不匹配的死条目.
     */
    @Test
    void testBlankResourceInRolesFailsFastAtInit() {
        properties.getRoles().put("admin", ":read");
        IllegalStateException ex = assertThrows(IllegalStateException.class, provider::init);
        assertTrue(ex.getMessage().contains("roles[admin]"), ex.getMessage());
        assertTrue(ex.getMessage().contains(":read"), ex.getMessage());

        properties.getRoles().put("admin", "user:r,:w");
        assertThrows(IllegalStateException.class, provider::init, "多段中任一 resource 为空都应 fail-fast");
    }

    @Test
    void testGetDataPermissionsByRoles() {
        properties.getUsers().put("1", "admin,user");
        properties.getDataScopes().put("admin", "order:ALL");
        properties.getDataScopes().put("user", "order:SELF,dept:read:DEPT");
        provider.init();

        Collection<DataPermission> dataPermissions = provider.getDataPermissions(createUser(1L));

        assertEquals(3, dataPermissions.size());
        assertTrue(dataPermissions.stream().anyMatch(dp -> dp.matches("order", "*") && "ALL".equals(dp.getDataScope())));
        assertTrue(dataPermissions.stream().anyMatch(dp -> dp.matches("order", "*") && "SELF".equals(dp.getDataScope())));
        assertTrue(dataPermissions.stream().anyMatch(dp -> dp.matches("dept", "read") && "DEPT".equals(dp.getDataScope())));
    }

    @Test
    void testInvalidDataScopeFailsFastAtInit() {
        properties.getDataScopes().put("admin", "order:WRONG");
        IllegalStateException scopeEx = assertThrows(IllegalStateException.class, provider::init);
        assertTrue(scopeEx.getMessage().contains("data-scopes[admin]"), scopeEx.getMessage());
        assertTrue(scopeEx.getMessage().contains("order:WRONG"), scopeEx.getMessage());

        properties.getDataScopes().put("admin", "badsegment");
        assertThrows(IllegalStateException.class, provider::init, "段数非法应 fail-fast");

        properties.getDataScopes().put("admin", ":DEPT");
        assertThrows(IllegalStateException.class, provider::init, "resource 为空应 fail-fast");
    }

    @Test
    void testNoDataScopesReturnsEmpty() {
        properties.getUsers().put("1", "admin");
        provider.init();

        assertTrue(provider.getDataPermissions(createUser(1L)).isEmpty());
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
