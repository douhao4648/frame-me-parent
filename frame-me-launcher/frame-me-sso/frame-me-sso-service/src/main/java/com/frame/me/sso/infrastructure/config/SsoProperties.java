package com.frame.me.sso.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

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

    /** 管理端点设备闸配置. */
    private DeviceGate deviceGate = new DeviceGate();

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

        /** 下游 token 独立时效（默认 1d，与浏览器登录会话分开）. */
        private Duration appTimeout = Duration.ofDays(1);
    }

    @Data
    public static class DeviceGate {

        /** 是否启用设备闸（管理端点仅接受默认设备会话），默认开. */
        private boolean enabled = true;

        /** 设备闸拦截路径. */
        private List<String> pathPatterns = List.of("/api/apps/**", "/api/auth/*/logout", "/api/users/**");
    }
}
