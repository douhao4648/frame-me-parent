package com.frame.me.auth.rbac.propagation;

import com.frame.me.auth.rbac.permission.AuthPermissionHolder;
import com.frame.me.auth.rbac.permission.Permission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;

import java.util.Set;

/**
 * 权限上下文异步任务装饰器.
 *
 * <p>在提交异步任务前捕获 {@link AuthPermissionHolder} 中的角色/权限，
 * 在异步线程执行前恢复、执行后清理，使 {@code @Async} 方法内的
 * {@code role()/perm()} 判断能够生效。需与 base 模块的组合式
 * {@code AsyncAutoConfiguration} 配合（多个 {@link TaskDecorator} 链式包裹）。</p>
 *
 * @author frame-me
 */
@Slf4j
public class AuthPermissionTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 仅在当前线程已加载权限上下文时才传播（含"已加载但为空"的情况）
        if (!AuthPermissionHolder.isLoaded()) {
            return runnable;
        }
        Set<String> roles = AuthPermissionHolder.getRoles();
        Set<Permission> permissions = AuthPermissionHolder.getPermissions();
        return () -> {
            try {
                AuthPermissionHolder.setRoles(roles);
                AuthPermissionHolder.setPermissions(permissions);
                AuthPermissionHolder.markLoaded();
                log.debug("权限上下文已传播到异步线程: roles={}", roles);
                runnable.run();
            } finally {
                AuthPermissionHolder.clear();
            }
        };
    }
}
