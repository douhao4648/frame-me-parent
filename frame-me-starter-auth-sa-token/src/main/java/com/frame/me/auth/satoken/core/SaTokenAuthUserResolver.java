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
 * <p>{@code token-prefix} 对齐原生 {@code StpLogic#getTokenValue} 语义：配置前缀后
 * header 通道未按前缀提交的 token 一律视为未提供（返回 null），提交的前缀剥离后透传，
 * 消除「原生读不到、AuthFilter 却放行」的反向裂缝。注意原生前缀匹配大小写敏感；
 * Cookie 通道存裸 token，不适用前缀（原生由 cookie-auto-fill-prefix 读写闭环）。</p>
 *
 * @author frame-me
 */
@RequiredArgsConstructor
public class SaTokenAuthUserResolver implements IAuthUserResolver {

    private final IAuthService authService;

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
        String headerToken = trimToNull(request.getHeader(SaManager.getConfig().getTokenName()));
        // 前缀模式仅约束 header 通道（对齐原生 getTokenValue）：未按前缀提交视为未提供
        if (headerToken != null) {
            return cutTokenPrefix(headerToken);
        }
        // Cookie 兜底对齐原生 sa-token.is-read-cookie 开关：
        // 业务显式关闭后原生不再读 Cookie，本框架也不读，避免开关被架空。
        // Cookie 存的是裸 token——原生读出时由 cookie-auto-fill-prefix 自动补前缀再裁剪，
        // 净效果即裸值透传，故 Cookie 通道不做前缀校验
        if (SaManager.getConfig().getIsReadCookie()) {
            return readTokenFromCookie(request, SaManager.getConfig().getTokenName());
        }
        return null;
    }

    /**
     * 剥离原生 {@code sa-token.token-prefix} 前缀，与 {@code StpLogic#getTokenValue} 对齐：
     * 配置前缀后未按 {@code "前缀 "}（含一个空格）开头提交的一律视为未提供 token，
     * 匹配成功后裁掉前缀再透传（原生匹配大小写敏感，此处保持一致）.
     */
    private static String cutTokenPrefix(String token) {
        if (token == null) {
            return null;
        }
        String prefix = SaManager.getConfig().getTokenPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return token;
        }
        if (!token.startsWith(prefix + " ")) {
            return null;
        }
        return trimToNull(token.substring(prefix.length() + 1));
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

    @Override
    public User resolve(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return null;
        }
        return authService.getUser(token);
    }
}
