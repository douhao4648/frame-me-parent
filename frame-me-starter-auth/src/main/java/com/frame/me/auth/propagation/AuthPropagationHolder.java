package com.frame.me.auth.propagation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证传播线程本地持有器.
 *
 * <p>用于在无线程绑定 {@code HttpServletRequest} 的上下文（如 {@code @Async} 线程）中，
 * 临时保存需要从当前请求传播到下游服务的请求头。</p>
 *
 * @author frame-me
 */
public class AuthPropagationHolder {

    private static final ThreadLocal<Map<String, String>> HEADERS = new ThreadLocal<>();

    private AuthPropagationHolder() {
    }

    /**
     * 设置当前线程需要传播的请求头.
     *
     * @param headers 请求头映射
     */
    public static void setHeaders(Map<String, String> headers) {
        HEADERS.set(headers);
    }

    /**
     * 获取当前线程中指定名称的请求头.
     *
     * @param name 头名称
     * @return 头值，不存在时返回 {@code null}
     */
    public static String getHeader(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Map<String, String> headers = HEADERS.get();
        return headers == null ? null : headers.get(name);
    }

    /**
     * 获取当前线程中所有待传播的请求头，只读视图.
     *
     * @return 请求头映射，无内容时返回空集合
     */
    public static Map<String, String> getHeaders() {
        Map<String, String> headers = HEADERS.get();
        // 直接包装原 Map 视图（无需拷贝）：ThreadLocal 的 Map 仅在 setHeaders/clear 时整体替换或移除，
        // 返回的视图绑定当前那份 Map 引用，后续 set/clear 不影响本视图
        return headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(headers);
    }

    /**
     * 清除当前线程的请求头.
     */
    public static void clear() {
        HEADERS.remove();
    }
}
