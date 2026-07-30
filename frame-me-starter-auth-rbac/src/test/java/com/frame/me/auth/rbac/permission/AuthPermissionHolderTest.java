package com.frame.me.auth.rbac.permission;

import com.frame.me.base.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AuthPermissionHolder} 单元测试.
 *
 * @author frame-me
 */
class AuthPermissionHolderTest {

    @AfterEach
    void tearDown() {
        AuthPermissionHolder.clear();
    }

    @Test
    void ensureLoaded_loadsOnce() {
        User user = new User();
        user.setId(1L);
        AuthPermissionHolder.ensureLoaded(user, provider(false));

        assertThat(AuthPermissionHolder.isLoaded()).isTrue();
        assertThat(AuthPermissionHolder.getRoles()).containsExactly("admin");
        assertThat(AuthPermissionHolder.getPermissions()).hasSize(1);
        assertThat(AuthPermissionHolder.getDataPermissions()).hasSize(1);
    }

    /**
     * 原子写入：provider 中途抛异常时不留半加载状态——
     * 角色集合不写入、loaded 不标记，线程复用时不会读到上一个用户的残留角色.
     */
    @Test
    void ensureLoaded_providerThrows_leavesNoPartialState() {
        User user = new User();
        user.setId(1L);

        assertThatThrownBy(() -> AuthPermissionHolder.ensureLoaded(user, provider(true)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(AuthPermissionHolder.isLoaded()).isFalse();
        assertThat(AuthPermissionHolder.getRoles()).isEmpty();
        assertThat(AuthPermissionHolder.getPermissions()).isEmpty();
        assertThat(AuthPermissionHolder.getDataPermissions()).isEmpty();
    }

    /**
     * 构造 provider；failOnPermissions 为 true 时 getPermissions 抛异常（模拟 DB/Redis 故障）.
     */
    private IAuthPermissionProvider provider(boolean failOnPermissions) {
        return new IAuthPermissionProvider() {
            @Override
            public Set<String> getRoles(User user) {
                return Set.of("admin");
            }

            @Override
            public List<Permission> getPermissions(User user) {
                if (failOnPermissions) {
                    throw new IllegalStateException("DB down");
                }
                return List.of(new Permission("order", "read"));
            }

            @Override
            public List<DataPermission> getDataPermissions(User user) {
                return List.of(new DataPermission("order", "read", "dept", Set.of(1L)));
            }
        };
    }
}
