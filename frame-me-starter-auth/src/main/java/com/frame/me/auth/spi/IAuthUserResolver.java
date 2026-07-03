package com.frame.me.auth.spi;

import com.frame.me.base.user.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前用户解析器接口.
 *
 * <p>从 HTTP 请求中解析当前登录用户。不同认证实现可自定义解析方式，
例如从 Header 读取、从 JWT 解析、从 Session 获取等。</p>
 *
 * @author frame-me
 */
public interface IAuthUserResolver {

    /**
     * 从请求中解析当前用户.
     *
     * @param request HTTP 请求
     * @return 当前用户，未登录或无效时返回 {@code null}
     */
    User resolve(HttpServletRequest request);
}
