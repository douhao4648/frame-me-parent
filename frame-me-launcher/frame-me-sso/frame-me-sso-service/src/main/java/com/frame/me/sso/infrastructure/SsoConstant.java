package com.frame.me.sso.infrastructure;

/**
 * SSO 服务常量.
 *
 * @author frame-me
 */
public final class SsoConstant {

    private SsoConstant() {
    }

    /** 授权码 Redis key 前缀. */
    public static final String AUTH_CODE_KEY_PREFIX = "sso:auth-code:";
}
