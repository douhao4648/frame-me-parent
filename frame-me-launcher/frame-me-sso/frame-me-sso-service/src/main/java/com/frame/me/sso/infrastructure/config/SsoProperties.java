package com.frame.me.sso.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SSO 服务配置属性.
 *
 * @author frame-me
 */
@Data
@ConfigurationProperties(prefix = "me.sso")
public class SsoProperties {

    /** 登录页配置. */
    private LoginPage loginPage = new LoginPage();

    /** 授权码配置. */
    private AuthCode authCode = new AuthCode();

    /** token 配置. */
    private Token token = new Token();

    @Data
    public static class LoginPage {

        private boolean enabled = true;
    }

    @Data
    public static class AuthCode {

        private Duration expires = Duration.ofSeconds(60);
    }

    @Data
    public static class Token {

        /** 下游 token 独立时效（默认 7d，与浏览器登录 2h 分开）. */
        private Duration appTimeout = Duration.ofDays(7);
    }
}
