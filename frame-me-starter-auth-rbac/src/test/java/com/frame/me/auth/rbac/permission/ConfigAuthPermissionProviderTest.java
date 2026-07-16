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

        User user = createUser(1L);
        Collection<Permission> permissions = provider.getPermissions(user);

        assertEquals(2, permissions.size());
    }

    @Test
    void testActionDefaultsToStar() {
        properties.getUsers().put("1", "admin");
        properties.getRoles().put("admin", "order");

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

        User user = createUser(1L);
        Collection<Permission> permissions = provider.getPermissions(user);

        assertEquals(2, permissions.size());
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
