package com.frame.me.auth.jwt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 认证配置属性.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.auth.jwt")
public class JwtAuthProperties {

    /**
     * 是否启用 JWT 认证，默认启用.
     */
    private Boolean enabled = true;

    /**
     * JWT 签名密钥，长度建议不少于 256 位.
     */
    private String secret;

    /**
     * Token 签发者.
     */
    private String issuer = "me";

    /**
     * Access Token 有效期，默认 2 小时.
     */
    private Duration accessTokenExpires = Duration.ofHours(2);

    /**
     * Refresh Token 有效期，默认 7 天.
     */
    private Duration refreshTokenExpires = Duration.ofDays(7);

    /**
     * 请求头中的 Token 前缀.
     */
    private String tokenHeader = "Authorization";

    /**
     * Token 前缀，默认 {@code Bearer }.
     */
    private String tokenPrefix = "Bearer ";

    /**
     * Redis 中 Refresh Token 的 key 前缀.
     */
    private String refreshTokenPrefix = "auth:refresh:";

    /**
     * Refresh Token 写入 Cookie 的 Domain.
     * <p>
     * 未配置时保持 JSON 返回 Refresh Token；配置后（如 {@code .example.com}）会把 Refresh Token
     * 作为 HttpOnly/Secure/SameSite=Lax Cookie 下发，JSON 中不再返回 Refresh Token。
     */
    private String cookieDomain;

    /**
     * JWT 认证接口基础路径，默认 {@code /api/auth}.
     * <p>
     * 必须以 {@code /} 开头，除根路径 {@code /} 外不能以 {@code /} 结尾。
     * 配置后，登录/登出/刷新/当前用户接口均会迁移到该路径下。
     */
    private String path = "/api/auth";
}
