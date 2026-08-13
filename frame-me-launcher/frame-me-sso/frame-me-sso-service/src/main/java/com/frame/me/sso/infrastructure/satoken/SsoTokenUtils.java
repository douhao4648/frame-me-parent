package com.frame.me.sso.infrastructure.satoken;

import com.frame.me.base.exception.BusinessException;
import com.frame.me.base.result.ResultCode;

/**
 * SSO Bearer token 工具：资源端点自验 token 的公共逻辑.
 *
 * <p>应用 token（deviceType=appId）走 {@code Authorization: Bearer} 通道，
 * 不进 sa-token 请求上下文（satoken 头/cookie 是浏览器会话通道），
 * 各资源端点用本类解析 + {@link SsoStpUtil#stpLogic} 原生验证（sso 账号体系）.</p>
 *
 * @author frame-me
 */
public final class SsoTokenUtils {

    /** 应用 token 的 loginId 前缀（client_credentials 颁发，无用户维度）. */
    public static final String APP_LOGIN_ID_PREFIX = "app:";

    /** Bearer 前缀（RFC 6750 §2.1，大小写不敏感匹配）. */
    private static final String BEARER_PREFIX = "Bearer ";

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
     * <p>按 RFC 6750 §2.1，Bearer 前缀大小写不敏感（{@code Bearer} / {@code bearer} /
     * {@code BEARER} 均应接受），与 JWT 侧 {@code JwtTokenService#extractToken} 的
     * {@code regionMatches(true,...)} 语义对齐——客户端发小写前缀不应导致验签失败.</p>
     *
     * @param authorization Authorization 头值，形如 "Bearer &lt;token&gt;"（兼容裸 token）
     * @return loginId（用户 token 为数字 userId 字符串，应用 token 为 "app:"+appId）
     * @throws BusinessException 401：头缺失、token 无效或已过期
     */
    public static String requireBearerLoginId(String authorization) {
        String token = extractBearerToken(authorization);
        Object loginId = token == null || token.isBlank() ? null : SsoStpUtil.stpLogic.getLoginIdByToken(token);
        if (loginId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "token 无效或已过期");
        }
        return loginId.toString();
    }

    /**
     * 从 Authorization 头剥离大小写不敏感的 Bearer 前缀，返回裸 token.
     *
     * <p>空头或仅前缀返回 {@code null}（空 token 让调用方走 401）.</p>
     */
    private static String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isEmpty()) {
            return null;
        }
        String prefix = BEARER_PREFIX;
        // regionMatches(ignoreCase=true) 对齐 RFC 6750 §2.1 与 JWT 侧实现
        if (authorization.length() >= prefix.length()
                && authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return authorization.substring(prefix.length()).trim();
        }
        return authorization.isBlank() ? null : authorization.trim();
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
