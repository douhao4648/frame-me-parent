package com.frame.me.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码工具.
 *
 * <p>基于 Spring Security Crypto 的 BCrypt 实现，strength 可由
 * {@code me.auth.password.bcrypt-strength} 配置（默认 12，符合 OWASP 当前推荐）。</p>
 *
 * @author frame-me
 */
public class PasswordUtils {

    private static volatile PasswordEncoder encoder = new BCryptPasswordEncoder(12);

    private PasswordUtils() {
    }

    /**
     * 替换 BCrypt 强度（由 {@link com.frame.me.auth.config.AuthAutoConfiguration} 启动时调用）.
     *
     * @param strength BCrypt log rounds，4-31
     */
    public static void setBcryptStrength(int strength) {
        encoder = new BCryptPasswordEncoder(strength);
    }

    /**
     * 加密原始密码.
     *
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public static String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * 校验密码.
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 匹配返回 {@code true}
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
