package com.frame.me.auth.jwt.web;

import com.frame.me.api.result.IResult;
import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.annotation.LoginUser;
import com.frame.me.auth.jwt.config.JwtAuthProperties;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.web.dto.LoginDTO;
import com.frame.me.auth.web.vo.TokenVO;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWT 认证控制器.
 *
 * <p><b>管理员强制登出接口（{@code /admin/logout/{userId}}）默认不做权限校验</b>，
 * 由业务方通过 {@code me.auth.permission.rules} 自行配置访问控制，
 * 避免 starter 强制依赖 RBAC 模块。</p>
 *
 * @author frame-me
 */
@Tag(name = "JWT 认证", description = "登录、登出、刷新 Token、获取当前用户、管理员强制登出")
@Validated
@RestController
@RequestMapping("${me.auth.jwt.path:/api/auth}")
@RequiredArgsConstructor
public class JwtAuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/";

    private final IAuthService authService;
    private final JwtAuthProperties properties;

    /**
     * 用户登录.
     */
    @Operation(summary = "登录", description = "账号密码登录，返回 Access Token；若配置了 cookie-domain，Refresh Token 会写入 HttpOnly Cookie")
    @Anonymous
    @PostMapping("/login")
    public IResult<TokenVO> login(@Valid @RequestBody LoginDTO dto, HttpServletResponse response) {
        String tokenPair = authService.login(dto.getAccount(), dto.getPassword());
        return Result.success(buildTokenResponse(tokenPair, response));
    }

    /**
     * 用户登出.
     */
    @Operation(summary = "登出", description = "使当前 Access Token 对应的 Refresh Token 失效；若配置了 cookie-domain，同时清除 Refresh Token Cookie")
    @PostMapping("/logout")
    public IResult<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = extractToken(request);
        authService.logout(token);
        if (isCookieMode()) {
            clearRefreshTokenCookie(response);
        }
        return Result.success(true);
    }

    /**
     * 刷新 Token.
     */
    @Operation(summary = "刷新 Token", description = "使用 Refresh Token 换取新的 Token 对；优先从 Authorization 头读取，否则尝试 HttpOnly Cookie")
    @Anonymous
    @PostMapping("/refresh")
    public IResult<TokenVO> refresh(
            @RequestHeader(value = "Authorization", required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        String token = resolveRefreshToken(refreshToken, request);
        String tokenPair = authService.refresh(token);
        return Result.success(buildTokenResponse(tokenPair, response));
    }

    /**
     * 获取当前登录用户.
     */
    @Operation(summary = "当前用户", description = "获取当前登录用户信息")
    @GetMapping("/user")
    public IResult<User> user(@LoginUser User user) {
        if (user == null) {
            return Result.error(ResultCode.UNAUTHORIZED, "未登录");
        }
        return Result.success(user);
    }

    /**
     * 管理员强制登出指定用户（清除 Refresh Token）.
     *
     * <p>默认不做权限校验，业务方应通过路径规则自行保护（如
     * {@code "[/api/auth/admin/**]": "role('admin')"}）。</p>
     */
    @Operation(summary = "强制登出用户", description = "管理员根据用户 ID 清除该用户的 Refresh Token；已颁发的 Access Token 仍会在自然过期前有效；默认无权限校验，需业务方自行配置路径规则")
    @PostMapping("/admin/logout/{userId}")
    public IResult<Boolean> logoutByUserId(
            @Parameter(description = "用户 ID", required = true)
            @PathVariable Long userId) {
        authService.logoutByUserId(userId);
        return Result.success(true);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(properties.getTokenHeader());
        if (header == null || !header.startsWith(properties.getTokenPrefix())) {
            return null;
        }
        return header.substring(properties.getTokenPrefix().length()).trim();
    }

    private String resolveRefreshToken(String headerToken, HttpServletRequest request) {
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }
        if (isCookieMode()) {
            return readRefreshTokenFromCookie(request);
        }
        return null;
    }

    private TokenVO buildTokenResponse(String tokenPair, HttpServletResponse response) {
        String[] parts = tokenPair.split(";");
        String accessToken = parts[0];
        String refreshToken = parts[1];
        if (isCookieMode()) {
            writeRefreshTokenCookie(response, refreshToken);
            return new TokenVO(accessToken, null);
        }
        return new TokenVO(accessToken, refreshToken);
    }

    private boolean isCookieMode() {
        String domain = properties.getCookieDomain();
        return domain != null && !domain.isBlank();
    }

    private void writeRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .domain(properties.getCookieDomain())
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(properties.getRefreshTokenExpires().getSeconds())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .domain(properties.getCookieDomain())
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
