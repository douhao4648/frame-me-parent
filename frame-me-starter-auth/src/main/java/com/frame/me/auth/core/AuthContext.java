package com.frame.me.auth.core;

import com.frame.me.base.user.User;

/**
 * 认证上下文.
 *
 * <p>基于 ThreadLocal 存储当前请求的用户信息，供业务层随时获取当前登录用户。</p>
 *
 * @author frame-me
 */
public class AuthContext {

    private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

    private AuthContext() {
    }

    /**
     * 获取当前登录用户.
     *
     * @return 当前用户，未登录时返回 {@code null}
     */
    public static User getUser() {
        return CURRENT_USER.get();
    }

    /**
     * 获取当前登录用户 ID.
     *
     * @return 用户 ID，未登录时返回 {@code null}
     */
    public static Long getUserId() {
        User user = CURRENT_USER.get();
        return user == null ? null : user.getId();
    }

    /**
     * 获取当前登录用户账号.
     *
     * @return 用户账号，未登录时返回 {@code null}
     */
    public static String getAccount() {
        User user = CURRENT_USER.get();
        return user == null ? null : user.getAccount();
    }

    /**
     * 设置当前登录用户.
     *
     * @param user 当前用户
     */
    public static void setUser(User user) {
        CURRENT_USER.set(user);
    }

    /**
     * 清除当前登录用户.
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
}
