package com.frame.me.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置（{@code me.gateway.auth.*}）.
 *
 * <p>凭证驱动模型：不按路由声明鉴权类型，按请求携带的凭证自动识别——
 * {@code Authorization: Signature ...}（APISIX hmac-auth 契约）走 app 验签，
 * 用户 token（Bearer / tokenName 头）走 user 校验，无凭证请求按 {@code allowAnonymous}
 * 处理（false=401，true=剥离身份头后匿名放行）。详见 docs/guides/gateway.md。</p>
 *
 * <p>实例级开关：{@code userAuthEnabled}/{@code appAuthEnabled} 关闭后本实例不再识别
 * 对应凭证（携带也 401），用于将来纯配置拆出 app 专属等实例，代码零改动。</p>
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.gateway.auth")
public class GatewayAuthProperties {

    /**
     * 是否启用用户凭证识别（默认 true）；false 时携带用户 token 也 401.
     */
    private boolean userAuthEnabled = true;

    /**
     * 是否启用 app 签名凭证识别（默认 true）；false 时携带合法签名也 401.
     */
    private boolean appAuthEnabled = true;

    /**
     * 用户 token 验证器，与下游统一认证底座对应（user-auth-enabled=true 时生效）.
     */
    private UserValidatorType userValidator = UserValidatorType.JWT;

    /**
     * 匿名白名单路径（Ant 风格），命中跳过鉴权（身份头仍剥离，防伪造）.
     */
    private List<String> whitelist = new ArrayList<>();

    /**
     * 无凭证请求是否匿名放行：false（默认）= 401 要求认证；true = 剥离身份头后放行
     * （默认仅 internal profile 下合法，见 anonymousGuardEnabled；携带合法凭证的请求不受影响，仍正常认证注入身份头）.
     */
    private boolean allowAnonymous = false;

    /**
     * 匿名放行守卫开关（默认 true）：true 时 {@code allow-anonymous=true} 必须显式激活
     * internal profile，否则启动失败；显式置 false 则跳过该拓扑校验（仅用于明确知晓
     * 风险的场景，关闭即放弃防呆保护）.
     */
    private boolean anonymousGuardEnabled = true;

    /**
     * JWT 验证器配置（user-validator=jwt 时生效）.
     */
    private Jwt jwt = new Jwt();

    /**
     * sa-token 验证器配置（user-validator=sa-token 时生效）.
     */
    private SaToken saToken = new SaToken();

    /**
     * 二三方应用凭证列表（app 验签）.
     */
    private List<AppCredential> apps = new ArrayList<>();

    /**
     * 用户 token 验证器类型.
     */
    public enum UserValidatorType {
        /**
         * 验下游 auth-jwt 签发的 JWT（jjwt 本地验签，无状态）.
         */
        JWT,
        /**
         * 查下游 auth-sa-token 的共享 Redis token key（踢人即时生效）.
         */
        SA_TOKEN
    }

    /**
     * JWT 验证器配置.
     *
     * <p>secret/issuer/tokenHeader/tokenPrefix 必须与下游 {@code me.auth.jwt.*} 一致
     * （经配置中心统一下发），否则验签/取头失败.</p>
     */
    @Data
    public static class Jwt {

        /**
         * JWT 签名密钥（HMAC，不少于 256 位），未配置或强度不足启动 fail-fast.
         */
        private String secret;

        /**
         * Token 签发者，与下游 {@code me.auth.jwt.issuer} 一致.
         */
        private String issuer = "me";

        /**
         * 请求头名，与下游 {@code me.auth.jwt.token-header} 一致.
         */
        private String tokenHeader = "Authorization";

        /**
         * Token 前缀（大小写不敏感剥离），与下游 {@code me.auth.jwt.token-prefix} 一致.
         */
        private String tokenPrefix = "Bearer ";
    }

    /**
     * sa-token 验证器配置.
     *
     * <p>直查共享 Redis key {@code {tokenName}:{logicType}:token:{token}}，
     * 与下游 sa-token 配置一致（工程现状为同一 Redis + loginType 命名空间隔离）.</p>
     */
    @Data
    public static class SaToken {

        /**
         * sa-token 的 token-name（同时是请求头名）.
         */
        private String tokenName = "satoken";

        /**
         * 下游 sa-token 账号体系（logic-type），默认 {@code login}.
         */
        private String logicType = "login";

        /**
         * Token 前缀（如下游配置了 {@code sa-token.token-prefix}，大小写不敏感剥离；未配置留空）.
         */
        private String tokenPrefix = "";
    }

    /**
     * 二三方应用凭证.
     */
    @Data
    public static class AppCredential {

        /**
         * 应用标识（签名参数的 {@code keyId}，认证通过后注入 {@code X-App-Key} 头）.
         */
        private String appKey;

        /**
         * 应用密钥（HMAC-SHA256 签名密钥，支持 sensi-encrypt 密文）.
         */
        private String secret;
    }
}
