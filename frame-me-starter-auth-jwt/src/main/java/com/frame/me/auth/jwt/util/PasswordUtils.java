package com.frame.me.auth.jwt.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码工具.
 *
 * <p>基于 Spring Security Crypto 的 BCrypt 实现。</p>
 *
 * @author frame-me
 */
public class PasswordUtils {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtils() {
    }

    /**
     * 加密原始密码.
     *
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验密码.
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 匹配返回 {@code true}
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
