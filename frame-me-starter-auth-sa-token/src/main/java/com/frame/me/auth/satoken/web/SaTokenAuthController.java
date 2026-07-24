package com.frame.me.auth.satoken.web;

import com.frame.me.api.result.IResult;
import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.annotation.LoginUser;
import com.frame.me.auth.config.AuthProperties;
import com.frame.me.auth.satoken.core.SaTokenAuthUserResolver;
import com.frame.me.auth.spi.IAuthService;
import com.frame.me.auth.web.dto.LoginDTO;
import com.frame.me.auth.web.vo.TokenVO;
import com.frame.me.base.result.Result;
import com.frame.me.base.result.ResultCode;
import com.frame.me.base.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sa-Token 认证控制器.
 *
 * <p>提供登录 / 登出 / 续期 / 当前用户 / 管理员强制登出五个默认接口。Token 一律从原生
 * {@code sa-token.token-name} 指定的请求头读取（默认 {@code satoken}），
 * 同名 Cookie 兜底读取（Resolver 层，与原生 is-read-cookie 行为对齐）。</p>
 *
 * <p><b>管理员强制登出接口（{@code /admin/logout/{userId}}）默认不做权限校验</b>，
 * 由业务方通过 {@code me.auth.sa-token.rules} 或 {@code me.auth.permission.rules}
 * 自行配置访问控制，避免 starter 强制依赖 RBAC 模块。</p>
 *
 * <p>原生 Cookie 支持：配置 {@code sa-token.cookie.*} 且
 * {@code sa-token.is-read-cookie=true}（默认）后，login 自动写 Cookie、
 * logout 自动清、refresh 自动刷（属性全部来自 {@code sa-token.cookie.*}，
 * Max-Age 由 is-lasting-cookie + timeout 派生）；Token 同时永远经 JSON body 返回，
 * 前端可继续使用 header 传递，双通道二选一。</p>
 *
 * @author frame-me
 */
@Tag(name = "Sa-Token 认证", description = "登录、登出、续期 Token、获取当前用户")
@Validated
@RestController
@RequestMapping("${me.auth.sa-token.path:/api/auth}")
@RequiredArgsConstructor
public class SaTokenAuthController {

    private final IAuthService authService;
    private final AuthProperties authProperties;

    /**
     * 用户登录.
     */
    @Operation(summary = "登录", description = "账号密码登录，返回 Sa-Token 会话 Token；开启 sa-token 原生 Cookie（sa-token.is-read-cookie=true，默认）后自动写入 Cookie")
    @Anonymous
    @PostMapping("/login")
    public IResult<TokenVO> login(@Valid @RequestBody LoginDTO dto) {
        String token = authService.login(dto.getAccount(), dto.getPassword());
        return Result.success(new TokenVO(token, null));
    }

    /**
     * 用户登出.
     */
    @Operation(summary = "登出", description = "注销当前 Token 对应的会话；开启 sa-token 原生 Cookie 后自动清除 Token Cookie")
    @PostMapping("/logout")
    public IResult<Boolean> logout(HttpServletRequest request) {
        authService.logout(SaTokenAuthUserResolver.extractToken(request));
        return Result.success(true);
    }

    /**
     * 续期 Token.
     *
     * <p>sa-token 会话模型无 Refresh Token 概念：对当前 Token 续绝对有效期并刷新
     * 最后活跃时间（重置闲置冻结窗口），原 Token 保持不变。</p>
     */
    @Operation(summary = "续期 Token", description = "对当前 Token 续期绝对有效期并重置闲置冻结窗口，返回原 Token；开启 sa-token 原生 Cookie 后自动刷新 Cookie")
    @Anonymous
    @PostMapping("/refresh")
    public IResult<TokenVO> refresh(HttpServletRequest request) {
        String token = authService.refresh(SaTokenAuthUserResolver.extractToken(request));
        return Result.success(new TokenVO(token, null));
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
     * 管理员强制登出指定用户（踢出该用户所有会话）.
     *
     * <p>默认不做权限校验，业务方应通过路径规则自行保护（如
     * {@code "[/api/auth/admin/**]": "role:admin"}）。</p>
     */
    @Operation(summary = "强制登出用户", description = "管理员根据用户 ID 强制踢出该用户的所有 Sa-Token 会话；默认关闭，需通过 me.auth.admin.logout.enabled=true 开启，开启后必须自行配置路径规则保护")
    @PostMapping("/admin/logout/{userId}")
    public IResult<Boolean> logoutByUserId(
            @Parameter(description = "用户 ID", required = true)
            @PathVariable Long userId) {
        AuthProperties.Admin admin = authProperties.getAdmin();
        if (admin == null || !Boolean.TRUE.equals(admin.getLogoutEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "管理员强制登出接口未启用");
        }
        authService.logoutByUserId(userId);
        return Result.success(true);
    }
}
