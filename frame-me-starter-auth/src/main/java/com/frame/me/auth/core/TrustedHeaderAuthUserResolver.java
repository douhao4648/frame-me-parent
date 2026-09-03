package com.frame.me.auth.core;

import com.frame.me.auth.spi.IAuthUserDetailsService;
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
 * <p>可选回源模式（{@code me.auth.trusted-header.fetch-details=true}，需容器中有
 * {@link IAuthUserDetailsService}）：拿到 {@code X-User-Id} 后调 {@code loadUserById}
 * 回源补全完整用户（account 等头里没有的字段），并顺带校验用户状态——
 * 用户不存在或已禁用时返回 {@code null}（fail-closed），修复纯头解析不校验禁用状态的盲区。
 * 适用于网关 sa-token 模式（只注入 {@code X-User-Id}）下需要 account 的下游。</p>
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

    /**
     * 回源用户详情服务；为 {@code null} 时纯头解析（原语义）.
     */
    private final IAuthUserDetailsService userDetailsService;

    public TrustedHeaderAuthUserResolver() {
        this(null);
    }

    public TrustedHeaderAuthUserResolver(IAuthUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public User resolve(HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        final Long id;
        try {
            id = Long.valueOf(userId);
        } catch (NumberFormatException e) {
            log.warn("请求头中用户 ID 格式非法: {}", userId);
            return null;
        }
        if (userDetailsService != null) {
            return loadFromSource(id);
        }
        User user = new User();
        user.setId(id);
        user.setAccount(request.getHeader(HEADER_USER_ACCOUNT));
        return user;
    }

    /**
     * 回源加载完整用户：不存在或已禁用均返回 {@code null}（fail-closed）.
     */
    private User loadFromSource(Long id) {
        User user = userDetailsService.loadUserById(id);
        if (user == null) {
            log.warn("身份头用户回源不存在: {}", id);
            return null;
        }
        if (!User.STATUS_ENABLED.equals(user.getStatus())) {
            log.warn("身份头用户已禁用: {}", id);
            return null;
        }
        return user;
    }
}
