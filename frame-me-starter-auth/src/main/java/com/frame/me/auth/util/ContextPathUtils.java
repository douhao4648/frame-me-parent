package com.frame.me.auth.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * context-path 相关的路径处理工具.
 *
 * @author frame-me
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ContextPathUtils {

    /**
     * 剥离路径模式中的 context-path 前缀，使其可用于应用内路径匹配.
     *
     * <p>用户配置白名单/权限规则时可能习惯性带上 context-path（如 {@code /app/api/**}），
     * 而匹配基于应用内路径（不含 context-path），此处做平滑兼容：
     * 仅当 pattern 等于 context-path 或以 {@code context-path + "/"} 开头时剥离，
     * 避免误伤 {@code /app2/**} 这类仅前缀相同的合法配置。</p>
     *
     * @param pattern     原始路径模式
     * @param contextPath 当前应用的 context-path，可为 {@code null} 或空
     * @return 剥离前缀后的路径模式；无 context-path 或未命中前缀时原样返回
     */
    public static String stripContextPath(String pattern, String contextPath) {
        if (pattern == null || contextPath == null || contextPath.isEmpty()) {
            return pattern;
        }
        if (pattern.equals(contextPath)) {
            return "/";
        }
        if (pattern.startsWith(contextPath + "/")) {
            return pattern.substring(contextPath.length());
        }
        return pattern;
    }
}
