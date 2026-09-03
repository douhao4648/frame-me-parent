package com.frame.me.gateway.config;

import java.util.List;

/**
 * 网关常量.
 *
 * @author frame-me
 */
public final class GatewayConstant {

    /**
     * 网关注入下游的用户身份头：用户 ID.
     */
    public static final String HEADER_USER_ID = "X-User-Id";
    /**
     * 网关注入下游的用户身份头：账号（JWT 模式可取到，sa-token 模式无）.
     */
    public static final String HEADER_USER_ACCOUNT = "X-User-Account";
    /**
     * 网关注入下游的应用身份头：appKey（仅注入用途；外部携带会被剥离，认证方式为
     * {@code Authorization: Signature} + {@code Date}，对齐 APISIX hmac-auth）.
     */
    public static final String HEADER_APP_KEY = "X-App-Key";
    /**
     * app 签名凭证的 Authorization scheme 前缀（凭证识别分发依据，对齐 APISIX hmac-auth）.
     */
    public static final String APP_AUTH_SCHEME = "Signature ";
    /**
     * 需要从外部请求无条件剥离的身份头（防伪造），认证通过后由网关重新注入.
     */
    public static final List<String> STRIPPED_HEADERS =
            List.of(HEADER_USER_ID, HEADER_USER_ACCOUNT, HEADER_APP_KEY);

    private GatewayConstant() {
    }
}
