package com.frame.me.auth.satoken.permission;

import com.frame.me.auth.satoken.config.SaTokenAuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConfigStpInterface} 角色 / 权限展开测试.
 *
 * @author frame-me
 */
class ConfigStpInterfaceTest {

    /**
     * 装配好 users / roles 配置的实例.
     */
    private ConfigStpInterface newStpInterface() {
        SaTokenAuthProperties properties = new SaTokenAuthProperties();
        properties.getUsers().put("1", "admin, operator");
        properties.getRoles().put("admin", "user:add,order:read");
        properties.getRoles().put("operator", "order:read");
        return new ConfigStpInterface(properties);
    }

    /**
     * 按用户 ID 查角色：命中配置，逗号分隔去空白去重.
     */
    @Test
    void getRoleList_configuredUser() {
        ConfigStpInterface stpInterface = newStpInterface();
        assertThat(stpInterface.getRoleList(1L, "login")).containsExactly("admin", "operator");
        // loginId 以字符串形式查配置，Integer 类型同样命中
        assertThat(stpInterface.getRoleList("1", "login")).containsExactly("admin", "operator");
    }

    /**
     * 权限按角色展开为权限码集合：原样透传、跨角色去重.
     */
    @Test
    void getPermissionList_expandsRolesAndDeduplicates() {
        ConfigStpInterface stpInterface = newStpInterface();
        assertThat(stpInterface.getPermissionList(1L, "login"))
                .containsExactly("user:add", "order:read");
    }

    /**
     * 未配置用户 / null loginId / 空配置：均返回空列表.
     */
    @Test
    void missingConfig_returnsEmpty() {
        ConfigStpInterface stpInterface = newStpInterface();
        assertThat(stpInterface.getRoleList(999L, "login")).isEmpty();
        assertThat(stpInterface.getPermissionList(999L, "login")).isEmpty();
        assertThat(stpInterface.getRoleList(null, "login")).isEmpty();
        assertThat(stpInterface.getPermissionList(null, "login")).isEmpty();

        ConfigStpInterface empty = new ConfigStpInterface(new SaTokenAuthProperties());
        assertThat(empty.getRoleList(1L, "login")).isEmpty();
        assertThat(empty.getPermissionList(1L, "login")).isEmpty();
    }
}
