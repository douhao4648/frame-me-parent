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
 *     <li>{@code role('admin')} — 检查是否拥有指定角色</li>
 *     <li>{@code perm('user')} — 检查是否拥有 {@code user} 资源的任意操作权限</li>
 *     <li>{@code perm('user', 'w')} — 检查是否拥有 {@code user} 资源的指定操作（{@code w}）权限</li>
 * </ul>
 *
 * <p>通配：权限点<b>授权侧</b>的 {@code resource}/{@code action} 支持 {@code *}
 * （如 {@code user:*} 表示该资源的全部操作）；表达式可用 {@code and} / {@code or} 组合。
 * 未登录访问受保护方法返回 401，已登录但无权限返回 403。</p>
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
