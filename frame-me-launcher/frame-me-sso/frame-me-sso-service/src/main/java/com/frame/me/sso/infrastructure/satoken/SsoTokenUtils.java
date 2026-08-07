package com.frame.me.sso.infrastructure.satoken;

import cn.dev33.satoken.stp.StpUtil;
import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;

/**
 * SSO Bearer token 工具：资源端点自验 token 的公共逻辑.
 *
 * <p>应用 token（deviceType=appId）走 {@code Authorization: Bearer} 通道，
 * 不进 sa-token 请求上下文（satoken 头/cookie 是浏览器会话通道），
 * 各资源端点用本类解析 + 原生 {@link StpUtil#getLoginIdByToken} 验证.</p>
 *
 * @author frame-me
 */
public final class SsoTokenUtils {

    /** 应用 token 的 loginId 前缀（client_credentials 颁发，无用户维度）. */
    public static final String APP_LOGIN_ID_PREFIX = "app:";

    private SsoTokenUtils() {
    }

    /**
     * 构造应用 token 的 loginId.
     */
    public static String appLoginId(String appId) {
        return APP_LOGIN_ID_PREFIX + appId;
    }

    /**
     * 判断 loginId 是否为应用 token（client_credentials 颁发，无用户维度）.
     */
    public static boolean isAppLoginId(Object loginId) {
        return loginId != null && loginId.toString().startsWith(APP_LOGIN_ID_PREFIX);
    }

    /**
     * 从 Authorization 头解析 Bearer token 并验证.
     *
     * @param authorization Authorization 头值，形如 "Bearer &lt;token&gt;"（兼容裸 token）
     * @return loginId（用户 token 为数字 userId 字符串，应用 token 为 "app:"+appId）
     * @throws BusinessException 401：头缺失、token 无效或已过期
     */
    public static String requireBearerLoginId(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : authorization;
        Object loginId = token == null || token.isBlank() ? null : StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "token 无效或已过期");
        }
        return loginId.toString();
    }

    /**
     * 同 {@link #requireBearerLoginId}，但要求必须是用户 token 并返回 userId.
     *
     * @throws BusinessException 401：token 无效，或为应用 token（无用户维度）
     */
    public static long requireBearerUserId(String authorization) {
        String loginId = requireBearerLoginId(authorization);
        if (isAppLoginId(loginId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "应用凭证无用户信息");
        }
        try {
            return Long.parseLong(loginId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "token 主体非法");
        }
    }
}
