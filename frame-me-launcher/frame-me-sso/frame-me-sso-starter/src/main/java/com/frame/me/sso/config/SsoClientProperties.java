package com.frame.me.sso.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SSO 客户端配置属性（{@code me.sso.client.*}）.
 *
 * <p>下游应用引 {@code frame-me-sso-starter} 后，在此配置本应用在 SSO 注册的
 * 凭证与 SSO 服务地址。{@code app-secret} 支持 {@code ME(密文)} 加密，
 * 引入 {@code frame-me-starter-sensi-encrypt} 后启动期自动解密。</p>
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.sso.client")
public class SsoClientProperties {

    /**
     * 是否启用 SSO 客户端能力，默认 true.
     */
    private boolean enabled = true;

    /**
     * SSO RP 登录端点（{@code POST /sso-login}）的基础路径，默认 {@code /api/auth}。
     *
     * <p>sso-starter 自治配置，不绑 sa-token/jwt 的 path——下游改认证实现 path
     * 时，本属性需同步配，确保 {@code /sso-login} 与下游默认认证 controller
     * （{@code /login} {@code /logout} {@code /user}）共路径。</p>
     */
    private String path = "/api/auth";

    /**
     * SSO 服务基础地址，如 {@code http://frame-me-sso:10010}.
     */
    private String baseUrl;

    /**
     * 本应用在 SSO 注册的应用 ID.
     */
    private String appId;

    /**
     * 本应用的密钥（INTERNAL/EXTERNAL 均有，支持 {@code ME(密文)} 加密）.
     */
    private String appSecret;

    /**
     * 授权码回调地址.
     */
    private String redirectUri;

    /**
     * client_credentials 应用 token 的本地缓存时长，默认 1 小时.
     *
     * <p>应用 token 由 {@code SsoAuthService#getAppToken} 缓存复用，过期后自动重取。
     * 必须显著小于 SSO 侧 {@code me.sso.token.app-timeout}（默认 1d），留出安全边际；
     * 缓存也避免高频重取触发 SSO 的 appId 维度限流（默认 5 次/60s）。</p>
     */
    private Duration appTokenCacheTtl = Duration.ofHours(1);
}
