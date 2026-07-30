package com.frame.me.auth.satoken.core;

import cn.dev33.satoken.SaManager;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.spi.IAuthUserResolver;
import com.frame.me.base.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Sa-Token 当前用户解析器.
 *
 * <p>token 头名取自原生配置单一事实源 {@code SaManager.getConfig().getTokenName()}
 * （官方 {@code sa-token.token-name}，默认 {@code satoken}）。</p>
 *
 * <p>刻意走显式 header 读取（{@code request.getHeader(tokenName)}）而非
 * {@code StpUtil.getTokenValue()}——后者依赖 sa-token 上下文已就绪，
 * 而框架 {@code AuthFilter}（order = HIGHEST_PRECEDENCE + 100）先于官方
 * {@code SaTokenContextFilter}（order = -104）执行，此时 sa-token 上下文尚未初始化。</p>
 *
 * <p>header 优先、同名 Cookie 兜底：header 缺失或空白时遍历 {@code request.getCookies()}
 * 按 tokenName 匹配取值。Cookie 兜底遵循原生 {@code is-read-cookie} 开关（默认 true），
 * 业务显式关闭后本框架同样不读 Cookie，与原生读取行为完全对齐，
 * 消除「注解校验过、AuthFilter 却 401」的行为裂缝。</p>
 *
 * @author frame-me
 */
@RequiredArgsConstructor
public class SaTokenAuthUserResolver implements IAuthUserResolver {

    private final IAuthService authService;

    @Override
    public User resolve(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return null;
        }
        return authService.getUser(token);
    }

    /**
     * 从请求中提取 Token：header 优先、同名 Cookie 兜底（头名取原生
     * {@code sa-token.token-name} 配置，trim 后判空）.
     *
     * <p>Controller 与 Resolver 共用的唯一提取入口，避免两处独立演进产生行为分叉。
     * Cookie 兜底与 sa-token 原生 {@code is-read-cookie} 读取行为对齐，
     * 消除「注解校验过、AuthFilter 却 401」以及「Cookie-only 客户端
     * logout/refresh 拿不到 token」的行为裂缝。</p>
     */
    public static String extractToken(HttpServletRequest request) {
        String token = trimToNull(request.getHeader(SaManager.getConfig().getTokenName()));
        // Cookie 兜底对齐原生 sa-token.is-read-cookie 开关：
        // 业务显式关闭后原生不再读 Cookie，本框架也不读，避免开关被架空
        if (token == null && SaManager.getConfig().getIsReadCookie()) {
            token = readTokenFromCookie(request, SaManager.getConfig().getTokenName());
        }
        return token;
    }

    /**
     * 从 Cookie 中按 tokenName 匹配读取 Token（trim 后判空）.
     */
    private static String readTokenFromCookie(HttpServletRequest request, String tokenName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (tokenName.equals(cookie.getName())) {
                return trimToNull(cookie.getValue());
            }
        }
        return null;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
