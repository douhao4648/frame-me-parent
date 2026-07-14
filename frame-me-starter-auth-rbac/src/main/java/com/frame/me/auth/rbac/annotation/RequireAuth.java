package com.frame.me.auth.rbac.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解，支持 SpEL 表达式.
 *
 * <p>可用函数：
 * <ul>
 *     <li>{@code role('admin')} — 检查角色</li>
 *     <li>{@code perm('user')} — 检查资源读权限，等同于 {@code perm('user', 'r')}</li>
 *     <li>{@code perm('user', 'w')} — 检查资源+操作权限</li>
 * </ul>
 *
 * <p>示例：
 * <pre>{@code
 * @RequireAuth("role('admin')")
 * @RequireAuth("perm('order', 'w')")
 * @RequireAuth("role('admin') or perm('user')")
 * @RequireAuth("role('admin') and perm('order', 'w')")
 * }</pre>
 *
 * @author frame-me
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuth {

    /**
     * SpEL 表达式，返回 {@code true} 表示放行.
     */
    String value();
}
