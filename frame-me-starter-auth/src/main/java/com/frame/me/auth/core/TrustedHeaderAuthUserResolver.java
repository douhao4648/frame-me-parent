package com.frame.me.auth.core;

import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.base.user.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 信任身份头的兜底用户解析器.
 *
 * <p>仅用于不直接对外暴露的内网服务间调用兜底，从请求头中读取用户 ID 和账号构建 {@link User}。
 * 该解析器无条件信任客户端传入的身份头，默认不装配，需显式配置
 * {@code me.auth.trusted-header.enabled=true} 开启；对外应用应使用 JWT、Sa-Token 等真实实现。</p>
 *
 * @author frame-me
 */
@Slf4j
public class TrustedHeaderAuthUserResolver implements IAuthUserResolver {

    /**
     * 请求头：用户 ID.
     */
    public static final String HEADER_USER_ID = "X-User-Id";

    /**
     * 请求头：用户账号.
     */
    public static final String HEADER_USER_ACCOUNT = "X-User-Account";

    @Override
    public User resolve(HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        String account = request.getHeader(HEADER_USER_ACCOUNT);
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        try {
            User user = new User();
            user.setId(Long.valueOf(userId));
            user.setAccount(account);
            return user;
        } catch (NumberFormatException e) {
            log.warn("请求头中用户 ID 格式非法: {}", userId);
            return null;
        }
    }
}
