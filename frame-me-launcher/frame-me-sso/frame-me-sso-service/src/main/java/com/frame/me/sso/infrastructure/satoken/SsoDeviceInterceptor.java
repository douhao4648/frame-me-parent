package com.frame.me.sso.infrastructure.satoken;

import cn.dev33.satoken.util.SaTokenConsts;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端点设备闸：仅接受默认设备（{@value SaTokenConsts#DEFAULT_LOGIN_DEVICE_TYPE}）的会话.
 *
 * <p>SSO 下发的应用 token（deviceType=appId）与用户浏览器会话同属 {@code sso} 账号体系、
 * loginId 相同，若被放入 {@code satoken} 头可越过 {@code @SaCheckRole} 拿到用户完整身份。
 * 本拦截器在角色校验之外加一道设备维度：管理端点只认 SSO 登录会话
 * （登录时未显式指定 deviceType 的默认设备），
 * 应用 token（client_credentials 与授权码换的下游 token）一律 403.</p>
 *
 * <p>匿名请求（无 sa-token 会话）直接放行：{@code @Anonymous} 端点自验 token
 * （如 {@code /api/users/info} 的 Bearer 调用），受保护端点由 {@code @SaCheckRole}
 * 拦 401，本闸只管"已登录但设备不对"的越权场景.</p>
 *
 * @author frame-me
 */
public class SsoDeviceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!SsoStpUtil.stpLogic.isLogin()) {
            return true;
        }
        String device = SsoStpUtil.stpLogic.getLoginDeviceType();
        if (!SaTokenConsts.DEFAULT_LOGIN_DEVICE_TYPE.equals(device)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "管理端点仅接受 SSO 登录会话");
        }
        return true;
    }
}
