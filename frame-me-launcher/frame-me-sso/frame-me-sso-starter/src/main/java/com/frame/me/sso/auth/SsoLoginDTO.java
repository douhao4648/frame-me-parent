package com.frame.me.sso.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SSO 登录请求 DTO（RP 场景）.
 *
 * <p>前端拿到 SSO 授权码后，POST {@code /api/auth/sso-login} 换取下游本地会话。
 * {@code appId} / {@code appSecret} / {@code redirectUri} 走服务端配置
 * （{@code me.sso.client.*}），不暴露给前端——{@code redirectUri} 必须与
 * authorize 跳转时用的地址一致（SSO 服务端换 token 时会比对），
 * 故收口到配置而非前端传入。</p>
 *
 * @author frame-me
 */
@Data
public class SsoLoginDTO {

    /**
     * SSO 授权码（一次性，60s 过期）.
     */
    @NotBlank(message = "授权码不能为空")
    private String code;
}
