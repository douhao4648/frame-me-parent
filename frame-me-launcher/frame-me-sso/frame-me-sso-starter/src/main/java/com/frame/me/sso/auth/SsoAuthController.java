package com.frame.me.sso.auth;

import com.frame.me.api.result.IResult;
import com.frame.me.auth.annotation.Anonymous;
import com.frame.me.auth.web.vo.TokenVO;
import com.frame.me.base.result.Result;
import com.frame.me.op.audit.annotation.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 认证控制器（code 换下游本地会话）.
 *
 * <p>提供 {@code POST /api/auth/sso-login}（基础路径由 {@code me.sso.client.path} 配置，默认 {@code /api/auth}）：
 * 前端传 SSO 授权码，后端换 SSO token → 取用户信息 → 建下游本地会话，返回下游自己的 token。</p>
 *
 * <p>默认 {@code SaTokenAuthController}（来自 frame-me-starter-auth-sa-token）的
 * {@code /logout} {@code /refresh} {@code /user} 本服务直接复用，本类只加 {@code /sso-login}。
 * <b>改 path 时需与下游认证 controller 同步配</b>（{@code me.sso.client.path} 与
 * {@code me.auth.sa-token.path} / {@code me.auth.jwt.path} 保持一致），确保端点共路径。</p>
 *
 * <p><b>审计</b>：{@code @AuditLog} 标注，{@code recordParams=false, recordResult=false}——
 * 授权码、签发的 token 均为敏感数据，不入审计记录（op-audit 为 optional 依赖，
 * 未引入时注解被 JVM 静默忽略）。</p>
 *
 * <p><b>认证实现通用</b>：依赖 {@link SsoAuthService} 调 {@code IAuthService.loginByUser}，
 * sa-token/JWT 两套实现均覆盖，下游任选其一。</p>
 *
 * @author frame-me
 */
@Tag(name = "SSO 认证", description = "SSO 授权码换本地会话")
@RestController
@RequestMapping("${me.sso.client.path:/api/auth}")
@RequiredArgsConstructor
public class SsoAuthController {

    private final SsoAuthService ssoAuthService;

    /**
     * SSO 登录：授权码换本地会话 token.
     *
     * @param dto 授权码（redirectUri 走配置，不暴露给前端）
     * @return token（sa-token 无 refresh token，refreshToken 恒 null）
     */
    @Operation(summary = "SSO 登录", description = "SSO 授权码换本地会话")
    @AuditLog(action = "SSO登录", category = "认证", description = "SSO 授权码换会话", recordParams = false, recordResult = false)
    @Anonymous
    @PostMapping("/sso-login")
    public IResult<TokenVO> ssoLogin(@Valid @RequestBody SsoLoginDTO dto) {
        String token = ssoAuthService.ssoLogin(dto.getCode());
        return Result.success(new TokenVO(token, null));
    }
}
